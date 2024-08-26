package com.depromeet.stonebed.domain.member.dao;

import com.depromeet.stonebed.domain.member.domain.Member;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long>, MemberRepositoryCustom {
    Optional<Member> findByOauthInfoOauthProviderAndOauthInfoOauthEmail(
            String oauthProvider, String email);
}
