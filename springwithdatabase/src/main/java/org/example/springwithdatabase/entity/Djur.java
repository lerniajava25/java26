package org.example.springwithdatabase.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "djur")
public class Djur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String namn;

    @Column(nullable = false)
    private String art;

    @Column
    private Integer alder;

    public Djur() {
    }

    public Djur(String namn, String art, Integer alder) {
        this.namn = namn;
        this.art = art;
        this.alder = alder;
    }

    public Long getId() {
        return id;
    }

    public String getNamn() {
        return namn;
    }

    public void setNamn(String namn) {
        this.namn = namn;
    }

    public String getArt() {
        return art;
    }

    public void setArt(String art) {
        this.art = art;
    }

    public Integer getAlder() {
        return alder;
    }

    public void setAlder(Integer alder) {
        this.alder = alder;
    }
}
