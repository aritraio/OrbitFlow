package com.orbitflow.unit;

import com.orbitflow.workspace.WorkspaceSecurityPolicy;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PermissionPolicyTest {
    private final WorkspaceSecurityPolicy policy = new WorkspaceSecurityPolicy();

    @Test
    void roleHierarchyOrdering() {
        assertThat(WorkspaceSecurityPolicy.rank("OWNER")).isGreaterThan(WorkspaceSecurityPolicy.rank("ADMIN"));
        assertThat(WorkspaceSecurityPolicy.rank("ADMIN")).isGreaterThan(WorkspaceSecurityPolicy.rank("MEMBER"));
        assertThat(WorkspaceSecurityPolicy.rank("MEMBER")).isGreaterThan(WorkspaceSecurityPolicy.rank("GUEST"));
    }

    @Test
    void onlyAdminsCanInvite() {
        assertThat(policy.canInvite("OWNER")).isTrue();
        assertThat(policy.canInvite("ADMIN")).isTrue();
        assertThat(policy.canInvite("MEMBER")).isFalse();
        assertThat(policy.canInvite("GUEST")).isFalse();
    }

    @Test
    void onlyOwnerCanDeleteWorkspace() {
        assertThat(policy.canDeleteWorkspace("OWNER")).isTrue();
        assertThat(policy.canDeleteWorkspace("ADMIN")).isFalse();
    }

    @Test
    void guestsCannotCreateTasksWithoutProjectAccess() {
        assertThat(policy.canCreateTask("GUEST", null)).isFalse();
        assertThat(policy.canCreateTask("GUEST", "MEMBER")).isTrue();
        assertThat(policy.canCreateTask("MEMBER", null)).isTrue();
    }

    @Test
    void deleteTaskPolicy() {
        assertThat(policy.canDeleteTask("ADMIN", null, false)).isTrue();
        assertThat(policy.canDeleteTask("MEMBER", null, true)).isTrue();
        assertThat(policy.canDeleteTask("MEMBER", null, false)).isFalse();
        assertThat(policy.canDeleteTask("GUEST", null, false)).isFalse();
    }
}
