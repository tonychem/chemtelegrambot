package ru.chemicalbase;

import org.junit.jupiter.api.Test;
import ru.chemicalbase.utils.ChemicalVision;

import static org.assertj.core.api.Assertions.assertThat;


public class ChemicalVisionTest {
    private final ChemicalVision vision = new ChemicalVision();

    @Test
    public void indolineIndoleTest() {
        String smilesFromDatabase = "C1CC2=CC=CC=C2N1";
        String smilesFromNeuralNetwork = "C1=CC=C2C(=C1)C=CN2";

        assertThat(vision.exactMoleculeMatch(smilesFromDatabase, smilesFromNeuralNetwork)).isFalse();
    }

    @Test
    public void fluorobenzeneTest() {
        String smilesFromDatabase = "FC1=C(C=O)C=CC=C1";
        String smilesFromNeuralNetwork = "C1=CC=C(C(=C1)C=O)F";

        assertThat(vision.exactMoleculeMatch(smilesFromDatabase, smilesFromNeuralNetwork)).isTrue();
    }
}
