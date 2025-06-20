package ruzicka;

import ruzicka.handlers.DocHandler;

import java.sql.Blob;
import java.util.Arrays;
import java.util.List;

/**
 * @author Adam
 * @since 2025-04-15
 */
public class Main {
    public static void main(String[] args) {
        //--------------------------------------------------------------------------------------------------------------
        DocHandler docHandler = new DocHandler();

        Integer docTypeId = 1; // 'faktura'
        String docFormat = "pdf";
        Blob docContent = null; // dm_doccontent.doccontent must be nullable

        // Document 1
        List<String> attrNames1 = Arrays.asList("Zakaznik", "Rok");
        List<String> attrValues1 = Arrays.asList("aaa", "2019");
        List<String> attrTypes1 = Arrays.asList("string", "long"); // Rok is 'long'

        // Document 2
        List<String> attrNames2 = Arrays.asList("Zakaznik", "Rok");
        List<String> attrValues2 = Arrays.asList("aaa", "2025");
        List<String> attrTypes2 = Arrays.asList("string", "long");


        List<String> attrNames3 = Arrays.asList("Zakaznik", "Rok");
        List<String> attrValues3 = Arrays.asList("bbb", "2025"); // Empty string for Zakaznik
        List<String> attrTypes3 = Arrays.asList("string", "long");

        // Document 4
        List<String> attrNames4 = Arrays.asList("Zakaznik", "Rok");
        List<String> attrValues4 = Arrays.asList("ccc", "2025"); //ccc and EMPTY for Rok causes an error
        List<String> attrTypes4 = Arrays.asList("string", "long");
//---------------------------------------------------------------------------------------------------------------

        try {
            System.out.println("Attempting to add Document 1 (aaa, 2019)...");
            docHandler.addDocument(docTypeId, docContent, docFormat,
                    attrValues1, attrNames1, attrTypes1);
            System.out.println("---- Finished processing Document 1 ----\n");

            System.out.println("Attempting to add Document 2 (aaa, 2025)...");
            docHandler.addDocument(docTypeId, docContent, docFormat,
                    attrValues2, attrNames2, attrTypes2);
            System.out.println("---- Finished processing Document 2 ----\n");

            System.out.println("Attempting to add Document 3 (bbb, 2025)...");
            docHandler.addDocument(docTypeId, docContent, docFormat,
                    attrValues3, attrNames3, attrTypes3);
            System.out.println("---- Finished processing Document 3 ----\n");

            System.out.println("Attempting to add Document 4 (ccc, 2025)...");
            docHandler.addDocument(docTypeId, docContent, docFormat,
                    attrValues4, attrNames4, attrTypes4);
            System.out.println("---- Finished processing Document 4 ----\n");

//            docHandler.deleteDocument(1);
//printout the tree structure of the documents

        } catch (Exception e) {
            System.err.println("=====================================");
            System.err.println("TOP LEVEL ERROR in Main: " + e.getMessage());
            e.printStackTrace();
            System.err.println("=====================================");
        }
    }
}