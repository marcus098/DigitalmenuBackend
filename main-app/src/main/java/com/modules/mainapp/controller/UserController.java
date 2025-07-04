package com.modules.mainapp.controller;

import com.modules.authmodule.request.*;
import com.modules.authmodule.service.AuthService;
import com.modules.authmodule.service.UserService;
import com.modules.common.responses.AuthResponse;
import com.modules.common.responses.DataResponse;
import com.modules.servletconfiguration.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api")
public class UserController {
    @Autowired
    private UserService userService;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private AuthService authService;


    @GetMapping("/user/check")
    public ResponseEntity<?> checkUser(@RequestHeader("Authorization") String authorizationHeader) {
        try {
            // Verifica che l'intestazione Authorization sia presente e valida
            if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(401).body("Missing or invalid Authorization header");
            }
            String token = authorizationHeader.substring(7);
            AuthResponse response = authService.checkToken(token);
            if(response == null)
                throw new Exception("Unauthorized");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
    }

    @GetMapping("/getAgencyName/{id}")
    public ResponseEntity<DataResponse<String>> getAgencyName(@PathVariable("id") long id){
        String value = userService.getAgencyNameById(id);
        return ResponseEntity.status(value == null ? 400 : 200).body(new DataResponse<>(value));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginUser loginUser) {
        DataResponse<AuthResponse> dataResponse = authService.login(loginUser.getEmailUsername(), loginUser.getPassword());
        return ResponseEntity.status(dataResponse.getData() == null ? 400 : 200).body(dataResponse.getData());
    }

    @PostMapping("/signupAgency")
    public ResponseEntity<?> signupAgency(
            @ModelAttribute SignupAgency signupAgency,
            @RequestParam(required = false) MultipartFile file
    ) {
        DataResponse<Boolean> registered = authService.signupAgency(signupAgency, file);
        return ResponseEntity.status(registered.getData() ? 200 : 400).body(registered.getData());
    }

    @PostMapping("/signupWaiter")
    public ResponseEntity<?> signupAgency(@RequestBody SignupWaiter signupWaiter) {
        DataResponse<Boolean> registered = authService.signupWaiter(signupWaiter);
        return ResponseEntity.status(registered.getData() ? 200 : 400).body(registered.getData());
    }

    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_WAITER')")
    @PostMapping("/users/updateData")
    public ResponseEntity<?> updateUserData(@RequestBody UpdateData updateData) {
        if (!updateData.validate()) {
            return ResponseEntity.status(400).body("Payload error");
        }

        DataResponse<Boolean> dataResponse = userService.updateProfile(updateData);
        return ResponseEntity.status(dataResponse.getData() ? 200 : 400).body(dataResponse.getData());
    }

    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_WAITER')")
    @PostMapping("/users/changePassword")
    public ResponseEntity<?> updateUserData(@RequestBody ChangePassword changePassword) {
        if (!changePassword.validate()) {
            return ResponseEntity.status(400).body("Payload error");
        }
        DataResponse<Boolean> dataResponse = userService.changePassword(changePassword);
        return ResponseEntity.status(dataResponse.getData() ? 200 : 400).body(dataResponse.getData());
    }

    //@PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_WAITER')")
    @GetMapping("/users/confirmEmail/{code}")
    public ResponseEntity<?> confirmEmail(@PathVariable("code") String code){
        return ResponseEntity.ok(authService.confirmEmailOtpCodeLink(code));
    }

    //@PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_WAITER')")
    @GetMapping("/users/confirmPhone/{code}")
    public ResponseEntity<?> confirmPhone(@PathVariable("code") String code){
        return ResponseEntity.ok(authService.confirmPhoneOtpCodeLink(code));
    }

    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_WAITER')")
    @GetMapping("/users/confirmGeneral/{code}")
    public ResponseEntity<?> confirmGeneral(@PathVariable("code") String code){
        return ResponseEntity.ok(authService.confirmGeneralOtpCodeLink(code));
    }

    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    @GetMapping("/users/confirmWaiterAdmin/{id}")
    public ResponseEntity<?> confirmWaiterAdmin(@PathVariable("id") long id){
        return ResponseEntity.ok(authService.confirmWaiterAdminRegistration(id));
    }

    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    @GetMapping("/users/getInviteUrlWaiter")
    public ResponseEntity<?> getInviteUrlWaiter(){
        return ResponseEntity.ok(new DataResponse<>(authService.getUrlInvite(false)));
    }

    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    @GetMapping("/users/getInviteUrlAdmin")
    public ResponseEntity<?> getInviteUrlAdmin(){
        return ResponseEntity.ok(new DataResponse<>(authService.getUrlInvite(true)));
    }

    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    @GetMapping("/users/getWaiters")
    public ResponseEntity<?> getWaiters(){
        return ResponseEntity.ok(new DataResponse<>(userService.getAllWaiters()));
    }

    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    @GetMapping("/users/getAdmins")
    public ResponseEntity<?> getAdmins(){
        return ResponseEntity.ok(new DataResponse<>(userService.getAllAdmins()));
    }

    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    @GetMapping("/users/deleteWaiter/{id}")
    public ResponseEntity<?> deleteWaiter(@PathVariable("id") long id){
        return ResponseEntity.ok(new DataResponse<>(userService.deleteWaiter(id)));
    }

    @GetMapping("/users/resendEmailVerification/{id}/{code}")
    public ResponseEntity<?> resendEmailVerification(@PathVariable("id") long id, @PathVariable("code") String code){
        return ResponseEntity.ok(new DataResponse<>(userService.resendEmailVerification(id, code)));
    }

//@GetMapping("/public/testemail")
//public ResponseEntity<?> testemail(){
//    userService.sendOtpToEmail(4);
//    return ResponseEntity.status(404).body("work in progress");
//}

}
