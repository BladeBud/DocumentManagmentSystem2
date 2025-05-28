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
           'EXISTS (SELECT 1 FROM dm_attrvaluestr avs JOIN dm_doctypeattr dta ON avs.iddoctypeattr' ||
           ' = dta.iddoctypeattr JOIN dm_docattr da_ref ON dta.iddocattr = da_ref.iddocattr WHERE avs.iddoc = d.iddoc ' ||
           'AND da_ref.attrname = ''Zakaznik'' AND avs.value IS NOT NULL)',  -- docIncludeCondition
           'SELECT DISTINCT av.value
            FROM dm_attrvaluestr av
            JOIN dm_doctypeattr dta ON av.iddoctypeattr = dta.iddoctypeattr
            JOIN dm_docattr da ON dta.iddocattr = da.iddocattr
            WHERE da.attrname = ?
            ORDER BY av.value'
       );

-- Year level - second layer under customers
INSERT INTO dm_deftreenode (idTree, idDefParentTreeNode, docIncludeCondition, nodeNameScript)
VALUES (
           1,  -- idTree
           2,  -- idDefParentTreeNode (points to customer node)
           'EXISTS (SELECT 1 FROM dm_attrvaluestr avs JOIN dm_doctypeattr dta ON avs.iddoctypeattr ' ||
           '= dta.iddoctypeattr JOIN dm_docattr da_ref ON dta.iddocattr = da_ref.iddocattr WHERE avs.iddoc = d.iddoc ' ||
           'AND da_ref.attrname = ''Zakaznik'' AND avs.value IS NOT NULL) AND EXISTS (SELECT 1 FROM dm_attrvaluelong avl' ||
           ' JOIN dm_doctypeattr dta2 ON avl.iddoctypeattr = dta2.iddoctypeattr JOIN dm_docattr da2_ref ON dta2.iddocattr' ||
           ' = da2_ref.iddocattr WHERE avl.iddoc = d.iddoc AND da2_ref.attrname = ''Rok'' AND avl.value IS NOT NULL)',  -- docIncludeCondition
           'SELECT DISTINCT CAST(av.value AS VARCHAR)
    FROM dm_attrvaluelong av
    JOIN dm_doctypeattr dta ON av.iddoctypeattr = dta.iddoctypeattr
    JOIN dm_docattr da ON dta.iddocattr = da.iddocattr
    WHERE da.attrname = ?
    ORDER BY av.value DESC'  -- nodeNameScript
       );