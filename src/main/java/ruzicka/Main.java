package ruzicka;

import ruzicka.creators.AttrCreator;
import ruzicka.creators.TypeCreator;
import ruzicka.handlers.DocHandler;

import java.sql.Blob;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Main application class to demonstrate the full functionality of the DMS.
 * @author Adam
 * @since 2025-04-15
 */
public class Main {
    public static void main(String[] args) {
        // Instantiate the handlers and creators
        DocHandler docHandler = new DocHandler();
        AttrCreator attrCreator = new AttrCreator();
        TypeCreator typeCreator = new TypeCreator();


        Blob docContent = null;

        System.out.println("********** DMS DEMO START **********");

        // ---- 1. SETTING UP METADATA ----
//        try {
//            System.out.println("\n[PHASE 1: METADATA SETUP]");
            // Note: These will throw exceptions if the attributes/types already exist.
            // attrCreator.createAttribute("Zakaznik", "string");
            // attrCreator.createAttribute("Rok", "long");
            // attrCreator.createAttribute("Oddeleni", "string"); // New attribute for multi-branch demo

            // typeCreator.createType(
            //     "faktura",
            //     "SELECT 'Faktura-' || ? || '-' || ?",
            //     Arrays.asList("Zakaznik", "Rok", "Oddeleni"),
            //     Arrays.asList(true, true, false)
            // );
//            System.out.println("Metadata setup is assumed to be done by init-db scripts.");
//        } catch (Exception e) {
//            System.out.println("Metadata setup skipped: " + e.getMessage());
//        }

        // ---- 2. ADDING DOCUMENTS ----
        System.out.println("\n[PHASE 2: ADDING DOCUMENTS]");
        addDocuments(docHandler, docContent);

        // ---- 3. PRINT INITIAL TREE STRUCTURES ----
        System.out.println("\n[PHASE 3: VIEWING INITIAL TREE STRUCTURE]");
        docHandler.printTreeStructure(1);
        docHandler.printTreeStructure(2);

        // ---- 4. DELETING A DOCUMENT ----
        System.out.println("\n[PHASE 4: DELETING A DOCUMENT]");
        // We will delete Document 2 (ID=2), which is the only document for customer 'bbb'.
        // This should cause the '/Root/bbb' node to be removed after deletion.
        try {
            System.out.println("Attempting to delete Document with ID = 2 ('Faktura-bbb-2023')...");
            docHandler.deleteDocument(2);
        } catch (Exception e) {
            System.err.println("An error occurred during document deletion.");
            e.printStackTrace();
        }

        // ---- 5. PRINT FINAL TREE STRUCTURES ----
        System.out.println("\n[PHASE 5: VIEWING FINAL TREE STRUCTURE]");
        System.out.println("Tree structure after deleting Document 2. Node '/bbb' should be gone from Tree 1.");
        docHandler.printTreeStructure(1);
        docHandler.printTreeStructure(2);

        System.out.println("********** DMS DEMO COMPLETE **********");
    }

    /**
     * Helper method to add a predefined set of documents.
     */
    private static void addDocuments(DocHandler docHandler, Blob docContent) {
        Integer docTypeId = 1; // Assuming 'faktura' has idDocType = 1
        String docFormat = "pdf";

        try {
            // Document 1: Belongs to aaa, 2024
            System.out.println("\n--- Adding Document 1 (aaa, 2024) ---");
            docHandler.addDocument(docTypeId, docContent, docFormat,
                    Arrays.asList("aaa", "2024"),
                    Arrays.asList("Zakaznik", "Rok"),
                    Arrays.asList("string", "long"));

            // Document 2: Belongs to bbb, 2023
            System.out.println("\n--- Adding Document 2 (bbb, 2023) ---");
            docHandler.addDocument(docTypeId, docContent, docFormat,
                    Arrays.asList("bbb", "2023"),
                    Arrays.asList("Zakaznik", "Rok"),
                    Arrays.asList("string", "long"));

            // Document 3: Belongs to aaa, 2025
            System.out.println("\n--- Adding Document 3 (aaa, 2025) ---");
            docHandler.addDocument(docTypeId, docContent, docFormat,
                    Arrays.asList("aaa", "2025"),
                    Arrays.asList("Zakaznik", "Rok"),
                    Arrays.asList("string", "long"));

            // Document 4: Customer 'ccc' - should ONLY appear in Tree 1, not Tree 2
            System.out.println("\n--- Adding Document 4 (ccc, 2024) ---");
            docHandler.addDocument(docTypeId, docContent, docFormat,
                    Arrays.asList("ccc", "2024"),
                    Arrays.asList("Zakaznik", "Rok"),
                    Arrays.asList("string", "long"));

            // Document 5: No 'Rok' - should be placed in the parent 'aaa' folder in Tree 1
            System.out.println("\n--- Adding Document 5 (aaa, no Rok) ---");
            docHandler.addDocument(docTypeId, docContent, docFormat,
                    Collections.singletonList("aaa"),
                    Collections.singletonList("Zakaznik"),
                    Collections.singletonList("string"));

        } catch (Exception e) {
            System.err.println("=====================================");
            System.err.println("TOP LEVEL ERROR in Main during document add: " + e.getMessage());
            e.printStackTrace();
            System.err.println("=====================================");
        }
    }
}