-- strom 1 Root, pak rozdeli na zakazniky (Zakaznik = AAA, Zakaznik = BBB) a pak na roky
--def tree 1-----------------------------------------------------------------------------------------------------------
-- create the tree
INSERT INTO dm_tree (treeName, treeType, ixFreeNode, TreeContent)
VALUES ('DocumentTree', 1, 0, '');

-- Root level - the top node of the tree
INSERT INTO dm_deftreenode (idTree, idDefParentTreeNode, docIncludeCondition, nodeNameScript)
VALUES (
           1,  -- idTree
           0,  -- idDefParentTreeNode (0 for root)
           'false',  -- docIncludeCondition
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
           'SELECT TAV.Value
                    FROM DM_Doc D
                    JOIN DM_AttrValueStr TAV ON D.idDoc = TAV.idDoc
                    JOIN DM_DocTypeAttr TDTA ON TAV.idDocTypeAttr = TDTA.idDocTypeAttr
                    JOIN DM_DocAttr TDA ON TDTA.idDocAttr = TDA.idDocAttr
                    WHERE D.idDoc = ? AND TDA.attrName = ''Zakaznik'''
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
           'SELECT CAST(TAV.Value AS VARCHAR)
                    FROM DM_Doc D
                    JOIN DM_AttrValueLong TAV ON D.idDoc = TAV.idDoc
                    JOIN DM_DocTypeAttr TDTA ON TAV.idDocTypeAttr = TDTA.idDocTypeAttr
                    JOIN DM_DocAttr TDA ON TDTA.idDocAttr = TDA.idDocAttr
                    WHERE D.idDoc = ? AND TDA.attrName = ''Rok'''  -- nodeNameScript
       );

--def tree 2-----------------------------------------------------------------------------------------------------------
-- create the tree
INSERT INTO dm_tree (treeName, treeType, ixFreeNode, TreeContent)
VALUES ('DocumentTreeReversed', 2, 0, '');

-- Root level - the top node of the tree - plus a condiition
INSERT INTO dm_deftreenode (idTree, idDefParentTreeNode, docIncludeCondition, nodeNameScript)
VALUES (
           2,  -- idTree
           0,  -- idDefParentTreeNode (0 for root)
           'EXISTS (
               SELECT 1
               FROM DM_AttrValueStr TAV
               JOIN DM_DocTypeAttr TDTA ON TAV.idDocTypeAttr = TDTA.idDocTypeAttr
               JOIN DM_DocAttr TDA ON TDTA.idDocAttr = TDA.idDocAttr
               WHERE TAV.idDoc = d.idDoc AND TDA.attrName = ''Zakaznik''
                 AND (TAV.Value = ''aaa'' OR TAV.Value = ''bbb'')
           )',  -- docIncludeCondition
           'SELECT ''RootConditionedForAAABBB'''  -- nodeNameScript (static name for root)
       );

-- Year level - first layer under customers
INSERT INTO dm_deftreenode (idTree, idDefParentTreeNode, docIncludeCondition, nodeNameScript)
VALUES (
           2,  -- idTree
           1,  -- idDefParentTreeNode (points to root node)
           'EXISTS (
               SELECT 1 FROM DM_AttrValueStr avs
               JOIN DM_DocTypeAttr dta ON avs.iddoctypeattr = dta.iddoctypeattr
               JOIN DM_DocAttr da_ref ON dta.iddocattr = da_ref.iddocattr
               WHERE avs.iddoc = d.idDoc AND da_ref.attrname = ''Zakaznik'' AND avs.value IS NOT NULL
           ) AND EXISTS (
               SELECT 1 FROM DM_AttrValueLong avl
               JOIN DM_DocTypeAttr dta2 ON avl.iddoctypeattr = dta2.iddoctypeattr
               JOIN DM_DocAttr da2_ref ON dta2.iddocattr = da2_ref.iddocattr
               WHERE avl.iddoc = d.idDoc AND da2_ref.attrname = ''Rok'' AND avl.value IS NOT NULL
           )',  -- docIncludeCondition
           'SELECT CAST(TAV.Value AS VARCHAR)
                    FROM DM_Doc D
                    JOIN DM_AttrValueLong TAV ON D.idDoc = TAV.idDoc
                    JOIN DM_DocTypeAttr TDTA ON TAV.idDocTypeAttr = TDTA.idDocTypeAttr
                    JOIN DM_DocAttr TDA ON TDTA.idDocAttr = TDA.idDocAttr
                    WHERE D.idDoc = ? AND TDA.attrName = ''Rok'''  -- nodeNameScript
       );

-- Customer level - second layer under root
INSERT INTO dm_deftreenode (idTree, idDefParentTreeNode, docIncludeCondition, nodeNameScript)
VALUES (
           2,  -- idTree
           2,  -- idDefParentTreeNode (points to year node)
           'EXISTS (
              SELECT 1 FROM DM_AttrValueStr avs
              JOIN DM_DocTypeAttr dta ON avs.iddoctypeattr = dta.iddoctypeattr
              JOIN DM_DocAttr da_ref ON dta.iddocattr = da_ref.iddocattr
              WHERE avs.iddoc = d.idDoc AND da_ref.attrname = ''Zakaznik'' AND avs.value IS NOT NULL
          )',  -- docIncludeCondition
           'SELECT TAV.Value
                    FROM DM_Doc D
                    JOIN DM_AttrValueStr TAV ON D.idDoc = TAV.idDoc
                    JOIN DM_DocTypeAttr TDTA ON TAV.idDocTypeAttr = TDTA.idDocTypeAttr
                    JOIN DM_DocAttr TDA ON TDTA.idDocAttr = TDA.idDocAttr
                    WHERE D.idDoc = ? AND TDA.attrName = ''Zakaznik'''
       );

