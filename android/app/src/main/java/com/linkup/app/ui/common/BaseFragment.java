package com.linkup.app.ui.common;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.linkup.app.data.Repository;

/** Parent of every fragment. Logs the Fragment lifecycle to Logcat under the tag "Lifecycle". */
public abstract class BaseFragment extends Fragment {

    private static final String LIFECYCLE = "Lifecycle";

    private Repository repository;

    private void log(String event) {
        Log.d(LIFECYCLE, "  " + getClass().getSimpleName() + "." + event);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        log("onAttach");
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        log("onCreate");
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        log("onCreateView/onViewCreated");
    }

    @Override
    public void onStart() {
        super.onStart();
        log("onStart");
    }

    @Override
    public void onResume() {
        super.onResume();
        log("onResume");
    }

    @Override
    public void onPause() {
        log("onPause");
        super.onPause();
    }

    @Override
    public void onStop() {
        log("onStop");
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        log("onDestroyView");
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        log("onDestroy");
        super.onDestroy();
    }

    @Override
    public void onDetach() {
        log("onDetach");
        super.onDetach();
    }

    protected Repository repo() {
        if (repository == null) repository = new Repository(requireContext());
        return repository;
    }
}
