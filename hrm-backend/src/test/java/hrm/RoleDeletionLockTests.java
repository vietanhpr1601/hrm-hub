package hrm;

import hrm.model.Role;
import hrm.model.User;
import hrm.model.UserRole;
import hrm.repository.RoleRepository;
import hrm.repository.UserRepository;
import hrm.service.AccessControlService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class RoleDeletionLockTests {
    @Test
    void deletionChecksMembershipAfterObtainingAccountChangeLock() {
        RoleRepository roles = mock(RoleRepository.class);
        UserRepository users = mock(UserRepository.class);
        Role role = new Role(); role.setId(UUID.randomUUID()); role.setName("LEGACY_TEST");
        User account = new User();
        UserRole membership = new UserRole(); membership.setRole(role); account.getUserRoles().add(membership);
        when(users.lockUsersForAccountChange()).thenReturn(List.of(account));
        when(roles.findById(role.getId())).thenReturn(Optional.of(role));
        assertThrows(IllegalArgumentException.class, () -> new AccessControlService(roles, users).deleteRole(role.getId()));
        InOrder order = inOrder(users, roles);
        order.verify(users).lockUsersForAccountChange();
        order.verify(roles).findById(role.getId());
        verify(roles, never()).save(any());
    }

    @Test
    void assignmentAcquiresTheSameLockBeforeReadingTheTargetUserAndRoles() {
        RoleRepository roles = mock(RoleRepository.class);
        UserRepository users = mock(UserRepository.class);
        UUID id = UUID.randomUUID();
        when(users.lockUsersForAccountChange()).thenReturn(List.of());
        when(users.findById(id)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> new AccessControlService(roles, users)
                .updateUserAccess(id, new AccessControlService.UserAccessUpdate(Set.of(), Map.of())));
        InOrder order = inOrder(users, roles);
        order.verify(users).lockUsersForAccountChange();
        order.verify(users).findById(id);
        verifyNoInteractions(roles);
    }
}
