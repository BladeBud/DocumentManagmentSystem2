create table DM_DocType
(
    idDocType serial
        primary key,
    docTypeName varchar(255) not null,
    docNameScript text not null
--         docNameScript varchar(255) not null
);

create table DM_DocAttr
(
    idDocAttr serial
        primary key,
    attrName varchar(255) not null,
    attrType varchar(255) not null
);

create table DM_DocTypeAttr
(
    idDocTypeAttr serial
        primary key,
    idDocType integer not null references DM_DocType,
    idDocAttr integer not null references DM_DocAttr,
    isRequired boolean not null
-- isScalar boolean not null
);

create table DM_Doc
(
    idDoc serial
        primary key,
    idDocType integer not null references DM_DocType,
    docName varchar(255) not null
);

create table DM_DocContent
(
    idDoc serial
        primary key references DM_Doc,
    docContent text not null,
    docFormat varchar(255) not null
);

create table DM_AttrValueLong
(
    idAttrValueLong serial
        primary key,
    idDoc serial not null references DM_Doc,
    idDocTypeAttr integer not null references DM_DocTypeAttr,
    Value bigint not null
);

create table DM_AttrValueStr
(
    idAttrValueStr serial
        primary key,
    idDoc serial not null references DM_Doc,
    idDocTypeAttr integer not null references DM_DocTypeAttr,
    Value text not null
);

create table DM_AttrValueDate
(
    idAttrValueDate serial
        primary key,
    idDoc serial not null references DM_Doc,
    idDocTypeAttr integer not null references DM_DocTypeAttr,
    Value date not null
);

create table DM_Tree
(
    idTree serial
        primary key,
    treeName varchar(255) not null,
    treeType integer not null,
    ixFreeNode integer not null,
    TreeContent text not null
);

create table DM_DefTreeNode
(
    idDefTreeNode serial
        primary key,
    idTree integer not null references DM_Tree,
    idDefParentTreeNode integer not null,
    docIncludeCondition text not null,
    nodeNameScript varchar(255) not null
--     nodeAccessCondition boolean not null
);

create table DM_NodeXPath
(
    idNodeXPath serial
        primary key,
    nodeXPath varchar(255) not null unique
);

create table DM_DocXPath
(
    idDocXPath serial
        primary key,
    idDoc serial not null,
    idNodeXPath integer not null references DM_NodeXPath
);

create table DM_NodeName
(
    idNodeName serial
        primary key,
    nodeName varchar(255) not null
);

create table DM_DocNode
(
    idDocNode serial
        primary key,
    idDoc integer not null references DM_Doc,
    ixNode integer not null,
    idTree integer not null references DM_Tree
);
