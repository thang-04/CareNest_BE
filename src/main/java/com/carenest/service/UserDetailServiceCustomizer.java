package com.carenest.service;

import com.carenest.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDetailServiceCustomizer implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * @param username email or phone number
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Spring Security hides this exception as BadCredentialsException, so login does not reveal unknown accounts.
        return userRepository.findByIdentifier(username)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy người dùng"));
    }
}
