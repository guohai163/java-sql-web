package org.guohai.javasqlweb.service.webauthn;

import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.COSEAlgorithmIdentifier;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.PublicKeyCredentialParameters;
import com.yubico.webauthn.data.PublicKeyCredentialType;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import com.yubico.webauthn.data.UserIdentity;
import jakarta.servlet.http.HttpServletRequest;
import org.guohai.javasqlweb.beans.Result;
import org.guohai.javasqlweb.beans.UserBean;
import org.guohai.javasqlweb.beans.UserLoginStatus;
import org.guohai.javasqlweb.beans.WebAuthnBean;
import org.guohai.javasqlweb.beans.WebAuthnRequestBean;
import org.guohai.javasqlweb.beans.WebAuthnRequestType;
import org.guohai.javasqlweb.dao.UserManageDao;
import org.guohai.javasqlweb.dao.WebAuthnDao;
import org.guohai.javasqlweb.dao.WebAuthnRequestDao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebAuthServiceTests {

    @Mock
    private UserManageDao userManageDao;

    @Mock
    private WebAuthnDao webAuthnDao;

    @Mock
    private WebAuthnRequestDao webAuthnRequestDao;

    @Mock
    private MyCredentialRepository myCredentialRepository;

    @Mock
    private HttpServletRequest httpServletRequest;

    @Test
    void getStoresAssertionRequestInSharedStore() throws Exception {
        WebAuthService webAuthService = createService();
        when(webAuthnRequestDao.saveRequest(any(WebAuthnRequestBean.class))).thenReturn(true);

        Result<String> result = webAuthService.get("session-1");

        assertTrue(result.getStatus());
        ArgumentCaptor<WebAuthnRequestBean> requestCaptor = ArgumentCaptor.forClass(WebAuthnRequestBean.class);
        verify(webAuthnRequestDao).saveRequest(requestCaptor.capture());
        WebAuthnRequestBean storedRequest = requestCaptor.getValue();
        assertEquals(WebAuthnRequestType.ASSERTION, storedRequest.getRequestType());
        assertEquals("session-1", storedRequest.getRequestKey());
        assertNotNull(storedRequest.getCreatedTime());
        assertNotNull(storedRequest.getExpireTime());

        AssertionRequest restored = AssertionRequest.fromJson(storedRequest.getRequestJson());
        assertEquals(restored.toCredentialsGetJson(), result.getData());
    }

    @Test
    void createStoresRegistrationRequestInSharedStore() throws Exception {
        WebAuthService webAuthService = createService();
        UserBean user = new UserBean();
        user.setUserName("alice");
        user.setLoginStatus(UserLoginStatus.LOGGED);
        when(myCredentialRepository.getCredentialIdsForUsername("alice")).thenReturn(Collections.emptySet());
        when(userManageDao.getUserByToken("token-1")).thenReturn(user);
        when(webAuthnRequestDao.saveRequest(any(WebAuthnRequestBean.class))).thenReturn(true);

        Result<String> result = webAuthService.create("token-1");

        assertTrue(result.getStatus());
        ArgumentCaptor<WebAuthnRequestBean> requestCaptor = ArgumentCaptor.forClass(WebAuthnRequestBean.class);
        verify(webAuthnRequestDao).saveRequest(requestCaptor.capture());
        WebAuthnRequestBean storedRequest = requestCaptor.getValue();
        assertEquals(WebAuthnRequestType.REGISTRATION, storedRequest.getRequestType());
        assertEquals("token-1", storedRequest.getRequestKey());

        PublicKeyCredentialCreationOptions restored =
                PublicKeyCredentialCreationOptions.fromJson(storedRequest.getRequestJson());
        assertEquals(restored.toCredentialsCreateJson(), result.getData());
    }

    @Test
    void signInConsumesStoredRequestAcrossServiceInstances() throws Exception {
        WebAuthService podA = createService();
        when(webAuthnRequestDao.saveRequest(any(WebAuthnRequestBean.class))).thenReturn(true);
        Result<String> ignored = podA.get("session-2");
        assertTrue(ignored.getStatus());

        ArgumentCaptor<WebAuthnRequestBean> requestCaptor = ArgumentCaptor.forClass(WebAuthnRequestBean.class);
        verify(webAuthnRequestDao).saveRequest(requestCaptor.capture());
        WebAuthnRequestBean savedByPodA = requestCaptor.getValue();

        WebAuthService spyPodB = spy(createService());
        WebAuthnRequestBean storedRequest = new WebAuthnRequestBean();
        storedRequest.setCode(11);
        storedRequest.setRequestType(WebAuthnRequestType.ASSERTION);
        storedRequest.setRequestKey("session-2");
        storedRequest.setRequestJson(savedByPodA.getRequestJson());
        storedRequest.setCreatedTime(new Date());
        storedRequest.setExpireTime(new Date(System.currentTimeMillis() + 60_000L));

        when(webAuthnRequestDao.getActiveRequestForUpdate(eq(WebAuthnRequestType.ASSERTION), eq("session-2"), any(Date.class)))
                .thenReturn(storedRequest)
                .thenReturn(null);
        when(webAuthnRequestDao.deleteByCode(11)).thenReturn(1);
        doReturn(new WebAuthService.AssertionVerificationResult(true, "alice"))
                .when(spyPodB).verifyAssertion(eq("assertion-body"), any(AssertionRequest.class));

        UserBean user = new UserBean();
        user.setUserName("alice");
        when(userManageDao.getUserByName("alice")).thenReturn(user);
        when(userManageDao.setUserToken(eq("alice"), any(String.class))).thenReturn(true);
        when(userManageDao.setUserLoginSuccess(any(String.class))).thenReturn(true);

        Result<UserBean> firstResult = spyPodB.signIn("assertion-body", "session-2");
        Result<UserBean> secondResult = spyPodB.signIn("assertion-body", "session-2");

        assertTrue(firstResult.getStatus());
        assertNotNull(firstResult.getData().getToken());
        assertFalse(secondResult.getStatus());
        assertEquals("passkey 请求已失效，请重新发起", secondResult.getMessage());

        ArgumentCaptor<AssertionRequest> assertionCaptor = ArgumentCaptor.forClass(AssertionRequest.class);
        verify(spyPodB).verifyAssertion(eq("assertion-body"), assertionCaptor.capture());
        assertEquals(AssertionRequest.fromJson(savedByPodA.getRequestJson()).toJson(), assertionCaptor.getValue().toJson());
    }

    @Test
    void signInRejectsBlankSessionKey() throws Exception {
        WebAuthService webAuthService = createService();

        Result<UserBean> result = webAuthService.signIn("assertion-body", "   ");

        assertFalse(result.getStatus());
        assertEquals("passkey 请求已失效，请重新发起", result.getMessage());
    }

    @Test
    void createRejectsBlankUserToken() {
        WebAuthService webAuthService = createService();

        Result<String> result = webAuthService.create("   ");

        assertFalse(result.getStatus());
        assertEquals("未登录", result.getMessage());
    }

    @Test
    void registerConsumesStoredRequestFromSharedStore() throws Exception {
        WebAuthService spyService = spy(createService());
        PublicKeyCredentialCreationOptions request = buildRegistrationRequest();
        WebAuthnRequestBean storedRequest = new WebAuthnRequestBean();
        storedRequest.setCode(21);
        storedRequest.setRequestType(WebAuthnRequestType.REGISTRATION);
        storedRequest.setRequestKey("token-2");
        storedRequest.setRequestJson(request.toJson());
        storedRequest.setCreatedTime(new Date());
        storedRequest.setExpireTime(new Date(System.currentTimeMillis() + 60_000L));

        when(webAuthnRequestDao.getActiveRequestForUpdate(eq(WebAuthnRequestType.REGISTRATION), eq("token-2"), any(Date.class)))
                .thenReturn(storedRequest);
        when(webAuthnRequestDao.deleteByCode(21)).thenReturn(1);
        doReturn(new WebAuthService.RegistrationVerificationResult(
                request.getUser().getId().getBase64(),
                "credential-1",
                "public-key-1"))
                .when(spyService).verifyRegistration(eq("register-body"), any(PublicKeyCredentialCreationOptions.class));

        UserBean user = new UserBean();
        user.setUserName("alice");
        when(userManageDao.getUserByToken("token-2")).thenReturn(user);
        when(webAuthnDao.addPublicKey(any(WebAuthnBean.class))).thenReturn(true);
        when(httpServletRequest.getHeader("User-Agent")).thenReturn("JUnit");

        Result<String> result = spyService.register("token-2", "register-body");

        assertTrue(result.getStatus());
        ArgumentCaptor<PublicKeyCredentialCreationOptions> requestCaptor =
                ArgumentCaptor.forClass(PublicKeyCredentialCreationOptions.class);
        verify(spyService).verifyRegistration(eq("register-body"), requestCaptor.capture());
        assertEquals(request.toJson(), requestCaptor.getValue().toJson());

        ArgumentCaptor<WebAuthnBean> webAuthnCaptor = ArgumentCaptor.forClass(WebAuthnBean.class);
        verify(webAuthnDao).addPublicKey(webAuthnCaptor.capture());
        assertEquals(request.getUser().getId().getBase64(), webAuthnCaptor.getValue().getUserHandle());
        assertEquals("credential-1", webAuthnCaptor.getValue().getCredentialId());
        assertEquals("public-key-1", webAuthnCaptor.getValue().getPublicKey());
    }

    @Test
    void signInCleansExpiredRequestWhenNoActiveRecordExists() throws Exception {
        WebAuthService webAuthService = createService();
        when(webAuthnRequestDao.getActiveRequestForUpdate(eq(WebAuthnRequestType.ASSERTION), eq("session-3"), any(Date.class)))
                .thenReturn(null);

        Result<UserBean> result = webAuthService.signIn("assertion-body", "session-3");

        assertFalse(result.getStatus());
        assertEquals("passkey 请求已失效，请重新发起", result.getMessage());
        verify(webAuthnRequestDao).deleteExpiredByTypeAndKey(eq(WebAuthnRequestType.ASSERTION), eq("session-3"), any(Date.class));
    }

    private WebAuthService createService() {
        WebAuthService webAuthService = new WebAuthService("jsw.gydev.cn", "https://jsw.gydev.cn", myCredentialRepository);
        ReflectionTestUtils.setField(webAuthService, "userDao", userManageDao);
        ReflectionTestUtils.setField(webAuthService, "webAuthnDao", webAuthnDao);
        ReflectionTestUtils.setField(webAuthService, "webAuthnRequestDao", webAuthnRequestDao);
        ReflectionTestUtils.setField(webAuthService, "httpServletRequest", httpServletRequest);
        return webAuthService;
    }

    private PublicKeyCredentialCreationOptions buildRegistrationRequest() {
        return PublicKeyCredentialCreationOptions.builder()
                .rp(RelyingPartyIdentity.builder().id("jsw.gydev.cn").name("Java Sql Web App").build())
                .user(UserIdentity.builder()
                        .name("alice")
                        .displayName("alice")
                        .id(new ByteArray("alice".getBytes()))
                        .build())
                .challenge(new ByteArray(new byte[]{1, 2, 3}))
                .pubKeyCredParams(Collections.singletonList(
                        PublicKeyCredentialParameters.builder()
                                .alg(COSEAlgorithmIdentifier.ES256)
                                .type(PublicKeyCredentialType.PUBLIC_KEY)
                                .build()))
                .build();
    }
}
