-- Creates Tree 1 and its definitions
WITH tree1_insert AS (
    INSERT INTO dm_tree (idtree, treeName, treeType, ixFreeNode, TreeContent)
        VALUES (1, 'DocumentTree', 1, 1, '') ON CONFLICT (idtree) DO NOTHING
), root1_def AS (
    INSERT INTO dm_deftreenode (idTree, idDefParentTreeNode, docIncludeCondition, nodeNameScript)
        SELECT 1, 0, 'true', 'SELECT ''Root'''
        ON CONFLICT (idtree, iddefparenttreenode, nodenamescript) DO NOTHING RETURNING iddeftreenode
), customer_def AS (
    INSERT INTO dm_deftreenode (idTree, idDefParentTreeNode, docIncludeCondition, nodeNameScript)
        SELECT 1, (SELECT iddeftreenode FROM root1_def), 'true', 'SELECT TAV.Value FROM DM_Doc D JOIN DM_AttrValueStr TAV ON D.idDoc = TAV.idDoc JOIN DM_DocTypeAttr TDTA ON TAV.idDocTypeAttr = TDTA.idDocTypeAttr JOIN DM_DocAttr TDA ON TDTA.idDocAttr = TDA.idDocAttr WHERE D.idDoc = ? AND TDA.attrName = ''Zakaznik'''
        ON CONFLICT (idtree, iddefparenttreenode, nodenamescript) DO NOTHING RETURNING iddeftreenode
)
INSERT INTO dm_deftreenode (idTree, idDefParentTreeNode, docIncludeCondition, nodeNameScript)
SELECT 1, (SELECT iddeftreenode FROM customer_def), 'true', 'SELECT CAST(TAV.Value AS VARCHAR) FROM DM_Doc D JOIN DM_AttrValueLong TAV ON D.idDoc = TAV.idDoc JOIN DM_DocTypeAttr TDTA ON TAV.idDocTypeAttr = TDTA.idDocTypeAttr JOIN DM_DocAttr TDA ON TDTA.idDocAttr = TDA.idDocAttr WHERE D.idDoc = ? AND TDA.attrName = ''Rok'''
ON CONFLICT (idtree, iddefparenttreenode, nodenamescript) DO NOTHING;


-- Creates Tree 2 and its definitions
WITH tree2_insert AS (
    INSERT INTO dm_tree (idtree, treeName, treeType, ixFreeNode, TreeContent)
        VALUES (2, 'DocumentTreeReversed', 2, 1, '') ON CONFLICT (idtree) DO NOTHING
), root2_def AS (
    INSERT INTO dm_deftreenode (idTree, idDefParentTreeNode, docIncludeCondition, nodeNameScript)
        SELECT 2, 0, 'EXISTS (SELECT 1 FROM DM_AttrValueStr TAV JOIN DM_DocTypeAttr TDTA ON TAV.idDocTypeAttr = TDTA.idDocTypeAttr JOIN DM_DocAttr TDA ON TDTA.idDocAttr = TDA.idDocAttr WHERE TAV.idDoc = d.idDoc AND TDA.attrName = ''Zakaznik'' AND (TAV.Value = ''aaa'' OR TAV.Value = ''bbb''))', 'SELECT ''RootConditionedForAAABBB'''
        ON CONFLICT (idtree, iddefparenttreenode, nodenamescript) DO NOTHING RETURNING iddeftreenode
), year2_def AS (
    INSERT INTO dm_deftreenode (idTree, idDefParentTreeNode, docIncludeCondition, nodeNameScript)
        SELECT 2, (SELECT iddeftreenode FROM root2_def), 'true', 'SELECT CAST(TAV.Value AS VARCHAR) FROM DM_Doc D JOIN DM_AttrValueLong TAV ON D.idDoc = TAV.idDoc JOIN DM_DocTypeAttr TDTA ON TAV.idDocTypeAttr = TDTA.idDocTypeAttr JOIN DM_DocAttr TDA ON TDTA.idDocAttr = TDA.idDocAttr WHERE D.idDoc = ? AND TDA.attrName = ''Rok'''
        ON CONFLICT (idtree, iddefparenttreenode, nodenamescript) DO NOTHING RETURNING iddeftreenode
)
INSERT INTO dm_deftreenode (idTree, idDefParentTreeNode, docIncludeCondition, nodeNameScript)
SELECT 2, (SELECT iddeftreenode FROM year2_def), 'true', 'SELECT TAV.Value FROM DM_Doc D JOIN DM_AttrValueStr TAV ON D.idDoc = TAV.idDoc JOIN DM_DocTypeAttr TDTA ON TAV.idDocTypeAttr = TDTA.idDocTypeAttr JOIN DM_DocAttr TDA ON TDTA.idDocAttr = TDA.idDocAttr WHERE D.idDoc = ? AND TDA.attrName = ''Zakaznik'''
ON CONFLICT (idtree, iddefparenttreenode, nodenamescript) DO NOTHING;