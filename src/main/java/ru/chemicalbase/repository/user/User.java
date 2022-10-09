package ru.chemicalbase.repository.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Entity
@Table(name = "users")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class User {
    @Id
    @Column(name = "chat_id", nullable = false)
    private long chatId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "accepted")
    private boolean accepted;
}
