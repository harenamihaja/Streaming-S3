CREATE TABLE IF NOT EXIST etudiant (
    idetudiant SMALLINT AUTO_INCREMENT PRIMARY KEY,
    nom VARCHAR(20),
    prenom VARCHAR(20),
    annee SMALLINT
);

CREATE OR REPLACE TABLE modepaye (
    idmodepaye SMALLINT AUTO_INCREMENT PRIMARY KEY,
    description VARCHAR(50)
);

CREATE OR REPLACE TABLE etudiantpaye (
    idetudiantpaiement SMALLINT AUTO_INCREMENT PRIMARY KEY,
    idetudiant SMALLINT,
    idmodepaye SMALLINT,
    annee SMALLINT
);

CREATE OR REPLACE TABLE editionpaye (
    ideditionpaye SMALLINT AUTO_INCREMENT PRIMARY KEY,
    idetudiant SMALLINT,
    montant DECIMAL,
    annee SMALLINT,
);