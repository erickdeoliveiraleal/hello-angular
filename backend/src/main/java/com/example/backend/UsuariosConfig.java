package com.example.backend;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

/**
 * Os usuários que podem entrar e como as senhas são conferidas.
 * Parecido com um security realm do WildFly (Elytron) ou um IdentityStore do Jakarta Security.
 */
@Configuration
public class UsuariosConfig {

	/**
	 * Hash BCrypt da senha de estudo do usuário erick (a senha em si está no README).
	 * O código guarda só o hash: não dá para descobrir a senha a partir dele, só conferir
	 * se uma senha digitada gera o mesmo resultado. O prefixo {bcrypt} diz qual algoritmo
	 * foi usado.
	 */
	static final String HASH_SENHA_ERICK = "{bcrypt}$2a$10$ss93esKcWmsySgpx0qcRKeQaSc/gzj24SvYmsDdeSed2bCmoSgw5e";

	/**
	 * De onde vêm os usuários. Aqui, uma lista fixa em memória com um usuário só.
	 * Num sistema real, seria uma implementação que busca no banco de dados.
	 * O InMemoryUserDetailsManager não diferencia maiúsculas no nome do usuário.
	 */
	@Bean
	UserDetailsService usuarios() {
		UserDetails erick = User.withUsername("erick")
			.password(HASH_SENHA_ERICK)
			.build();
		return new InMemoryUserDetailsManager(erick);
	}

	/**
	 * Confere senhas pelo algoritmo indicado no prefixo do hash ({bcrypt}, {argon2}...).
	 * Para gerar hashes novos, usa BCrypt.
	 */
	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

}
