package ruzicka;

/**
 * @author Adam
 * @since 2025-04-15
 */

import ruzicka.creators.DocCreator;

import java.sql.Blob;
import java.util.Arrays;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        DocCreator docCreator = new DocCreator();

        // Common document properties
        Integer docTypeId = 1;
        String docFormat = "pdf";


        List<String> attrNames1 = Arrays.asList("Zakaznik", "Rok");
        List<String> attrValues1 = Arrays.asList("aaa", "2019");
        List<String> attrTypes1 = Arrays.asList("string", "long");


        List<String> attrNames2 = Arrays.asList("Zakaznik", "Rok");
        List<String> attrValues2 = Arrays.asList("bbb", "2025");
        List<String> attrTypes2 = Arrays.asList("string", "long");


        List<String> attrNames3 = Arrays.asList("Zakaznik", "Rok");
        List<String> attrValues3 = Arrays.asList("", "2025");
        List<String> attrTypes3 = Arrays.asList("string", "long");

        try {
            Blob docContent = null;

            long doc1Id = docCreator.createDocument(docTypeId, docContent, docFormat,
                    attrValues1, attrNames1, attrTypes1);
            System.out.println("Created document in aaa folder with ID: " + doc1Id);

            long doc2Id = docCreator.createDocument(docTypeId, docContent, docFormat,
                    attrValues2, attrNames2, attrTypes2);
            System.out.println("Created document in bbb2025 folder with ID: " + doc2Id);

            long doc3Id = docCreator.createDocument(docTypeId, docContent, docFormat,
                    attrValues3, attrNames3, attrTypes3);
            System.out.println("Created document in root folder with ID: " + doc3Id);

        } catch (Exception e) {
            System.err.println("Error creating documents: " + e.getMessage());
            e.printStackTrace();
        }
    }
}