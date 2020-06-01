package io.fqsh.quickSort;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.fqsh.Application;
import io.fqsh.Utils;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

@Singleton
public class QuickSortSettingsPanel {
    private JPanel quickSortSettingsPanel;
    private final List<JComboBox<Integer>> quickSortSettingsPanelComboBoxes = new ArrayList<>();
    private JButton quickSortSettingsPanelCalculateButton;
    private final List<Integer> samples = Arrays.asList(50, 60, 70, 80, 90);
    public static final int SAMPLE_MIN_VALUE = 5;
    public static final int SAMPLE_MAX_VALUE = 100;

    @Inject
    private Application application;

    @Inject
    private QuickSortChartsPanel quickSortChartsPanel;

    @Inject
    private QuickSortTablePanel quickSortTablePanel;

    @Inject
    private QuickSortConsolePanel quickSortConsolePanel;

    public JPanel build() {
        buildSettingsPanel();
        buildComboBoxes();
        buildCalculateButton();

        return quickSortSettingsPanel;
    }

    private void buildSettingsPanel() {
        quickSortSettingsPanel = new JPanel();
        quickSortSettingsPanel.setLayout(new GridLayout(1, 6, 10, 10));
        quickSortSettingsPanel.setBorder(new CompoundBorder(
            BorderFactory.createTitledBorder(
                quickSortSettingsPanel.getBorder(),
                "Liczba elementów do posortowania (w tys.)",
                TitledBorder.CENTER,
                TitledBorder.TOP
            ),
            new EmptyBorder(10, 10, 10, 10)
        ));
    }

    private void buildComboBoxes() {
        IntStream.rangeClosed(0, 4).forEach(index -> {
            JComboBox<Integer> comboBox = createRangeComboBox(index, SAMPLE_MIN_VALUE, SAMPLE_MAX_VALUE);

            quickSortSettingsPanelComboBoxes.add(comboBox);
            quickSortSettingsPanel.add(comboBox);
        });
    }

    private JComboBox<Integer> createRangeComboBox(int index, int min, int max) {
        JComboBox<Integer> comboBox = new JComboBox<>(
            IntStream.rangeClosed(min, max).boxed().toArray(Integer[]::new)
        );
        comboBox.setSelectedItem(samples.get(index));
        comboBox.addItemListener(itemEvent -> {
            if (itemEvent.getStateChange() == ItemEvent.SELECTED) {
                clearDataAfterActionIfValuesAreComputed();

                Integer value = (Integer) itemEvent.getItem();
                samples.set(index, value);

                quickSortChartsPanel.updateIterationChart();
                quickSortChartsPanel.updateRecursiveChart();

                quickSortTablePanel.setCellValueAt(index, 0, value);
            }
        });

        return comboBox;
    }

    private void buildCalculateButton() {
        quickSortSettingsPanelCalculateButton = new JButton("Oblicz");
        quickSortSettingsPanelCalculateButton.addActionListener(actionEvent -> {
            if (samples.stream().distinct().count() < 5) {
                JOptionPane.showMessageDialog(null, "Wybrane wartości nie powinny się powtarzać!");

                return;
            }

            clearDataAfterActionIfValuesAreComputed();
            calculate();
        });
        quickSortSettingsPanel.add(quickSortSettingsPanelCalculateButton);
    }

    private void calculate() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> blockUI());
        executor.execute(() -> quickSortConsolePanel.write("Rozpoczęto sortowanie."));
        IntStream.rangeClosed(0, 4).forEach(index -> {
            int elementsInThousands = samples.get(index);
            int[] unsortedDataForIteration = simplyDataGenerator(elementsInThousands);
            int[] unsortedDataForRecursion = unsortedDataForIteration.clone();

            executor.execute(() -> quickSortIterationWrapper(elementsInThousands, unsortedDataForIteration));
            executor.execute(() -> quickSortRecursiveWrapper(elementsInThousands, unsortedDataForRecursion));
        });
        executor.execute(() -> quickSortConsolePanel.write("Zakończono sortowanie."));
        executor.execute(() -> unblockUI());
        executor.shutdown();
    }

    private int[] simplyDataGenerator(int elementsInThousands) {
        return (new Random()).ints(elementsInThousands * 1000, 0, 100_000).toArray();
    }

    private void quickSortIterationWrapper(int n, int[] unsortedData) {
        int index = samples.indexOf(n);

        long startTime = System.nanoTime();
        QuickSortIteration.calculate(unsortedData, 0, unsortedData.length - 1);
        long finishTime = System.nanoTime();

        long timeElapsed = finishTime - startTime;

        quickSortChartsPanel.setIterationTimeAt(index, timeElapsed);
        quickSortChartsPanel.updateIterationChart();
        quickSortTablePanel.setCellValueAt(index, 1, Utils.convertTime(timeElapsed));

        quickSortConsolePanel.write(String.format(
            "Losowe elementy o rozmiarze: %,d tys. zostały posortowane iteracyjnie w czasie: %s.",
            n,
            Utils.convertTime(timeElapsed)
        ));
    }

    private void quickSortRecursiveWrapper(int n, int[] unsortedData) {
        int index = samples.indexOf(n);

        long startTime = System.nanoTime();
        QuickSortRecursive.calculate(unsortedData, 0, unsortedData.length - 1);
        long finishTime = System.nanoTime();

        long timeElapsed = finishTime - startTime;

        quickSortChartsPanel.setRecursiveTimeAt(index, timeElapsed);
        quickSortChartsPanel.updateRecursiveChart();
        quickSortTablePanel.setCellValueAt(index, 2, Utils.convertTime(timeElapsed));

        quickSortConsolePanel.write(String.format(
            "Losowe elementy o rozmiarze: %,d tys. zostały posortowane rekurencyjnie w czasie: %s.",
            n,
            Utils.convertTime(timeElapsed)
        ));
    }

    private void clearDataAfterActionIfValuesAreComputed() {
        if (quickSortChartsPanel.getIterationTimes().stream().noneMatch(value -> value.equals(0L))) {
            quickSortChartsPanel.resetData();
            quickSortChartsPanel.updateIterationChart();
            quickSortChartsPanel.updateRecursiveChart();
            quickSortTablePanel.setCellsToNoDataState();
            quickSortConsolePanel.clearConsole();
        }
    }

    private void blockUI() {
        application.blockTabbedPane();
        blockAllComboBoxes();
        blockCalculateButton();
        quickSortTablePanel.setCellsToCalculatingState();
        quickSortConsolePanel.clearConsole();
    }

    private void unblockUI() {
        application.unblockTabbedPane();
        unblockAllComboBoxes();
        unblockCalculateButton();
    }

    private void blockAllComboBoxes() {
        IntStream.rangeClosed(0, 4).forEach(index -> {
            quickSortSettingsPanelComboBoxes.get(index).setEnabled(false);
        });
    }

    private void unblockAllComboBoxes() {
        IntStream.rangeClosed(0, 4).forEach(index -> {
            quickSortSettingsPanelComboBoxes.get(index).setEnabled(true);
        });
    }

    private void blockCalculateButton() {
        quickSortSettingsPanelCalculateButton.setEnabled(false);
    }

    private void unblockCalculateButton() {
        quickSortSettingsPanelCalculateButton.setEnabled(true);
    }

    public List<Integer> getSamples() {
        return samples;
    }
}
