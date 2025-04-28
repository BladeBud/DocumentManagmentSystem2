insert into dm_doctype values ( '1','faktura', 'SELECT
    dt.name as doctype_name,
    da.value as zakaznik_value
FROM dm_doctype dt
JOIN dm_doctypeattr dta ON dt.id = dta.doctype_id
JOIN dm_docattr da ON dta.attr_id = da.id
WHERE da.name = ''Zakaznik''
ORDER BY dt.id;');
insert into dm_doctype values ( '2','zapis_z_jednani', 'ZapisJednaniScript');
insert into dm_doctype values ( '3','projektova_dokumentace', 'ProjektovaDokumentaceScript');

insert into dm_docattr values ('1','Zakaznik', 'string');
insert into dm_docattr values ('2','Rok', 'int');
insert into dm_docattr values ('3','Projekt', 'string');
insert into dm_docattr values ('4','Subsystem', 'string');
insert into dm_docattr values ('5','Datum_splatnosti', 'date');


insert into dm_doctypeattr values (1, 1,1, true);
insert into dm_doctypeattr values (2, 1,2, true);
insert into dm_doctypeattr values (3, 1,3, true);
insert into dm_doctypeattr values (4, 1,5, true);

insert into dm_doctypeattr values (5, 2,1, true);
insert into dm_doctypeattr values (6, 2,2, true);
insert into dm_doctypeattr values (7, 2,3, true);

insert into dm_doctypeattr values (8, 3,1, true);
insert into dm_doctypeattr values (9, 3,3, true);
insert into dm_doctypeattr values (10, 3,4, true);
