package ru.chemicalbase.repository.reagent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
public interface ReagentRepository extends JpaRepository<Reagent, Long> {
    @Query("select r from Reagent r where upper(r.name) like concat('%', upper(:text), '%') " +
            "or upper(r.alternativeName) like concat('%', upper(:text), '%') " +
            "or upper(r.anotherName) like concat('%', upper(:text), '%')")
    List<Reagent> search(String text);
    List<Reagent> findAllBySmilesNotNull();
}
