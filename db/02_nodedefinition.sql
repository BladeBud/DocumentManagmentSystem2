-- strom 1 Root, pak rozdeli na zakazniky (Zakaznik = AAA, Zakaznik = BBB) a pak na roky

-- create the tree
INSERT INTO dm_tree (treeName, treeType, ixFreeNode, TreeContent)
VALUES ('DocumentTree', 1, 0, '');

-- Root level - the top node of the tree
INSERT INTO dm_deftreenode (idTree, idDefParentTreeNode, docIncludeCondition, nodeNameScript)
VALUES (
           1,  -- idTree
           0,  -- idDefParentTreeNode (0 for root)
           'false',  -- docIncludeCondition (root doesn't contain documents)
           'SELECT ''Root'''  -- nodeNameScript (static name for root)
       );

-- Customer level - first layer under root
INSERT INTO dm_deftreenode (idTree, idDefParentTreeNode, docIncludeCondition, nodeNameScript)
VALUES (
           1,  -- idTree
           1,  -- idDefParentTreeNode (points to root node)
           'EXISTS (
               SELECT 1
               FROM dm_attrvaluestr av
               JOIN dm_doctypeattr dta ON av.iddoctypeattr = dta.iddoctypeattr
               JOIN dm_docattr da ON dta.iddocattr = da.iddocattr
               WHERE av.iddoc = dm_doc.iddoc
               AND da.attrname = ''Zakaznik''
               AND av.value = :node_name
           )',  -- docIncludeCondition
           'SELECT DISTINCT av.value
           FROM dm_attrvaluestr av
           JOIN dm_doctypeattr dta ON av.iddoctypeattr = dta.iddoctypeattr
           JOIN dm_docattr da ON dta.iddocattr = da.iddocattr
           WHERE da.attrname = ''Zakaznik''
           ORDER BY av.value'  -- nodeNameScript
       );

-- Year level - second layer under customers
INSERT INTO dm_deftreenode (idTree, idDefParentTreeNode, docIncludeCondition, nodeNameScript)
VALUES (
           1,  -- idTree
           2,  -- idDefParentTreeNode (points to customer node)
           'EXISTS (
               SELECT 1
               FROM dm_attrvaluestr av1
               JOIN dm_doctypeattr dta1 ON av1.iddoctypeattr = dta1.iddoctypeattr
               JOIN dm_docattr da1 ON dta1.iddocattr = da1.iddocattr
               WHERE av1.iddoc = dm_doc.iddoc
               AND da1.attrname = ''Zakaznik''
               AND av1.value = :parent_node_name
           )
           AND EXISTS (
               SELECT 1
               FROM dm_attrvaluelong av2
               JOIN dm_doctypeattr dta2 ON av2.iddoctypeattr = dta2.iddoctypeattr
               JOIN dm_docattr da2 ON dta2.iddocattr = da2.iddocattr
               WHERE av2.iddoc = dm_doc.iddoc
               AND da2.attrname = ''Rok''
               AND CAST(av2.value AS VARCHAR) = :node_name
           )',  -- docIncludeCondition
           'SELECT DISTINCT CAST(av.value AS VARCHAR)
           FROM dm_attrvaluelong av
           JOIN dm_doctypeattr dta ON av.iddoctypeattr = dta.iddoctypeattr
           JOIN dm_docattr da ON dta.iddocattr = da.iddocattr
           WHERE da.attrname = ''Rok''
           ORDER BY av.value DESC'  -- nodeNameScript
       );