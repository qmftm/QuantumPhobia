package me.qmftm.quantumPhobia;

import me.qmftm.quantumPhobia.command.QuantumPhobiaCommand;
import me.qmftm.quantumPhobia.gui.PhobiaListMenu;
import me.qmftm.quantumPhobia.hud.StatusBar;
import me.qmftm.quantumPhobia.insanity.InsanityManager;
import me.qmftm.quantumPhobia.insanity.WillpowerManager;
import me.qmftm.quantumPhobia.integration.QuantumAddersEffects;
import me.qmftm.quantumPhobia.integration.QuantumAddersItems;
import me.qmftm.quantumPhobia.medicine.MedicationManager;
import me.qmftm.quantumPhobia.medicine.OverdoseSymptoms;
import me.qmftm.quantumPhobia.medicine.OverdoseTracker;
import me.qmftm.quantumPhobia.medicine.SedativeListener;
import me.qmftm.quantumPhobia.phobia.PhobiaCureListener;
import me.qmftm.quantumPhobia.phobia.PhobiaDiagnosisListener;
import me.qmftm.quantumPhobia.phobia.PhobiaManager;
import me.qmftm.quantumPhobia.phobia.PhobiaSymptoms;
import me.qmftm.quantumPhobia.stress.StressMonitor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class QuantumPhobia extends JavaPlugin {

    private PhobiaManager phobiaManager;
    private OverdoseSymptoms overdoseSymptoms;
    private PhobiaSymptoms phobiaSymptoms;
    private StatusBar statusBar;
    private StressMonitor stressMonitor;

    @Override
    public void onLoad() {
        QuantumAddersItems.install(this);
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();

        phobiaManager = new PhobiaManager(this);
        phobiaManager.load();
        phobiaManager.startExpiryTask();

        PhobiaListMenu listMenu = new PhobiaListMenu();
        getServer().getPluginManager().registerEvents(listMenu, this);
        WillpowerManager willpower = new WillpowerManager(this);
        InsanityManager insanity = new InsanityManager(this, willpower);
        getServer().getPluginManager().registerEvents(insanity, this);
        overdoseSymptoms = new OverdoseSymptoms(this, insanity);
        overdoseSymptoms.start();
        getServer().getPluginManager().registerEvents(overdoseSymptoms, this);
        OverdoseTracker overdose = new OverdoseTracker(this, createEffects(), overdoseSymptoms, insanity);
        getServer().getPluginManager().registerEvents(overdose, this);
        MedicationManager medication = new MedicationManager(this);
        getServer().getPluginManager().registerEvents(new PhobiaCureListener(this, medication, overdose, insanity), this);
        getServer().getPluginManager().registerEvents(new SedativeListener(this, insanity), this);
        phobiaSymptoms = new PhobiaSymptoms(this, phobiaManager, medication, insanity);
        phobiaSymptoms.start();
        getServer().getPluginManager().registerEvents(phobiaSymptoms, this);
        stressMonitor = new StressMonitor(this, insanity, willpower, phobiaManager, medication, overdoseSymptoms);
        stressMonitor.start();
        getServer().getPluginManager().registerEvents(stressMonitor, this);
        statusBar = new StatusBar(this, overdoseSymptoms, willpower);
        statusBar.start();
        getServer().getPluginManager().registerEvents(new PhobiaDiagnosisListener(phobiaManager), this);

        QuantumPhobiaCommand command = new QuantumPhobiaCommand(phobiaManager, listMenu, insanity, willpower);
        getCommand("qph").setExecutor(command);
        getCommand("qph").setTabCompleter(command);
    }

    /** QuantumAddersEffects links against QuantumAdders, so it is only touched when that plugin is running. */
    private QuantumAddersEffects createEffects() {
        Plugin adders = getServer().getPluginManager().getPlugin("QuantumAdders");
        if (adders == null || !adders.isEnabled()) {
            getLogger().warning("QuantumAdders가 없어 상태효과를 표시할 수 없습니다.");
            return null;
        }
        return new QuantumAddersEffects(this, adders);
    }

    @Override
    public void onDisable() {
        if (phobiaSymptoms != null) {
            phobiaSymptoms.stop();
        }
        if (statusBar != null) {
            statusBar.stop();
        }
        if (stressMonitor != null) {
            stressMonitor.stop();
        }
        if (overdoseSymptoms != null) {
            // Hallucinated phantoms must not outlive the plugin as real, visible mobs.
            overdoseSymptoms.shutdown();
        }
        phobiaManager.stopExpiryTask();
        phobiaManager.save();
    }
}
