package net.twmaps.plus;

import android.content.Intent;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;

public class ActivityLauncher {
    private final ActivityResultLauncher<Intent> launcherIntent;
    private final ActivityResultLauncher<String> launcherPermission;
    private final ActivityResultLauncher<String[]> launcherMultiplePermissions;

    private OnActivityResult<ActivityResult> onActivityResultIntent;
    private OnActivityResult<Boolean> onActivityResultPermission;
    private OnActivityResult<Map<String, Boolean>> onActivityResultMultiplePermissions;

    public ActivityLauncher(@NonNull ActivityResultCaller caller) {
        this.launcherIntent = caller.registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), this::callOnActivityResultIntent);
        this.launcherPermission = caller.registerForActivityResult(new ActivityResultContracts.RequestPermission(), this::callOnActivityResultPermission);
        this.launcherMultiplePermissions = caller.registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), this::callOnActivityResultMultiplePermissions);
    }

    public void launch(Intent intent, @Nullable OnActivityResult<ActivityResult> onActivityResult) {
        this.onActivityResultIntent = onActivityResult;
        launcherIntent.launch(intent);
    }

    public void launch(String permission, @Nullable OnActivityResult<Boolean> onActivityResult) {
        this.onActivityResultPermission = onActivityResult;
        launcherPermission.launch(permission);
    }

    public void launch(String[] permissions, @Nullable OnActivityResult<Map<String, Boolean>> onActivityResult) {
        this.onActivityResultMultiplePermissions = onActivityResult;
        launcherMultiplePermissions.launch(permissions);
    }

    private void callOnActivityResultIntent(ActivityResult result) {
        if (onActivityResultIntent != null) onActivityResultIntent.onActivityResult(result);
    }

    private void callOnActivityResultPermission(Boolean result) {
        if (onActivityResultPermission != null) onActivityResultPermission.onActivityResult(result);
    }

    private void callOnActivityResultMultiplePermissions(Map<String, Boolean> result) {
        if (onActivityResultMultiplePermissions != null) onActivityResultMultiplePermissions.onActivityResult(result);
    }

    public interface OnActivityResult<O> {
        void onActivityResult(O result);
    }
}