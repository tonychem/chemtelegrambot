package ru.chemicalbase.repository.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByChatId(long chatId);

    @Query("select u.accepted from User u where u.chatId = :chatId ")
    boolean getAcceptedByChatId(long chatId);
}
