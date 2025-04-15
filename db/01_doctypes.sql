insert into dm_doctype values ( '1','faktura');
insert into dm_doctype values ( '2','zapis_z_jednani');
insert into dm_doctype values ( '3','projektova_dokumentace');

insert into dm_docattr values ('1','Zakaznik', 'string');
insert into dm_docattr values ('2','Rok', 'int');
insert into dm_docattr values ('3','Projekt', 'string');
insert into dm_docattr values ('4','Subsystem', 'string');
insert into dm_docattr values ('5','Datum_splatnosti', 'date');


insert into dm_doctypeattr values (1, 1,1);
insert into dm_doctypeattr values (2, 1,2);
insert into dm_doctypeattr values (3, 1,3);
insert into dm_doctypeattr values (4, 1,5);

insert into dm_doctypeattr values (5, 2,1);
insert into dm_doctypeattr values (6, 2,2);
insert into dm_doctypeattr values (7, 2,3);

insert into dm_doctypeattr values (8, 3,1);
insert into dm_doctypeattr values (9, 3,3);
insert into dm_doctypeattr values (10, 3,4);
