package ru.chemicalbase.repository;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Entity
@Table(name = "reagents")
@Data
@NoArgsConstructor
public class Reagent {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private String name;

    private String formula;

    @Column(name = "alternative_name")
    private String alternativeName;

    @Column(name = "another_name")
    private String anotherName;

    private String remainder;

    private String room;

    private String shed;

    private String shelf;

    private String commentary;

    private String cas;

    private String smiles;
}
