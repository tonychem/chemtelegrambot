package ru.chemicalbase.Utils;

import com.epam.indigo.Indigo;
import com.epam.indigo.IndigoObject;
import gov.nih.ncats.molvec.Molvec;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;

@Component
public class ChemicalVision {
    private final Indigo indigo = new Indigo();

    /**
     * Метод возвращает smiles молекулы, разрешенный с помощью химического зрения
     * @param file - файл с изображением
     * @return smiles
     * @throws IOException
     */
    public String parseImage(File file) throws IOException {
        String mol = Molvec.ocr(file);
        IndigoObject obj = indigo.loadMolecule(mol);
        return obj.smiles();
    }

    /**
     * Метод сравнивает smiles двух молекул проверяя, являются ли они идентичными
     * @param firstMolecule - smiles первой молекулы
     * @param secondMolecule - smiles второй молекулы
     * @return являются ли две молекулы идентичными
     */
    public boolean exactMoleculeMatch(String firstMolecule, String secondMolecule) {
        IndigoObject target = indigo.loadMolecule(firstMolecule);
        IndigoObject query = indigo.loadMolecule(secondMolecule);
        IndigoObject match = indigo.exactMatch(target, query, "NONE");

        return match != null;
    }
}
