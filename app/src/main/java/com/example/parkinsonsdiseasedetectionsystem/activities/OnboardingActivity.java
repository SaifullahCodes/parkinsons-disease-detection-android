package com.example.parkinsonsdiseasedetectionsystem.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.example.parkinsonsdiseasedetectionsystem.R;
import com.example.parkinsonsdiseasedetectionsystem.adapters.OnboardingAdapter;
import com.example.parkinsonsdiseasedetectionsystem.models.OnboardingItem;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.List;

public class OnboardingActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private OnboardingAdapter adapter;
    private TextView btnSkip;
    private FloatingActionButton btnNext;
    private TabLayout tabLayout;
    private List<OnboardingItem> itemList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        viewPager = findViewById(R.id.view_pager);
        btnSkip = findViewById(R.id.btn_skip);
        btnNext = findViewById(R.id.btn_next);
        tabLayout = findViewById(R.id.tab_layout);

        setupItems();

        adapter = new OnboardingAdapter(itemList);
        viewPager.setAdapter(adapter);

        // ✅ Setup TabLayout Dots
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            tab.setCustomView(R.layout.dot_tab);
        }).attach();

        // ✅ Skip button click
        btnSkip.setOnClickListener(v -> finishOnboarding());

        // ✅ Next button click
        btnNext.setOnClickListener(v -> {
            int pos = viewPager.getCurrentItem();
            if (pos < itemList.size() - 1) {
                viewPager.setCurrentItem(pos + 1);
            } else {
                finishOnboarding();
            }
        });

        // ✅ Update dots on page change
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                for (int i = 0; i < tabLayout.getTabCount(); i++) {
                    ImageView dot = tabLayout.getTabAt(i).getCustomView().findViewById(R.id.dot_image);
                    if (i == position) {
                        dot.setBackgroundResource(R.drawable.dot_selected);
                    } else {
                        dot.setBackgroundResource(R.drawable.dot_unselected);
                    }
                }
            }
        });
    }

    // ✅ Add your slides here
    private void setupItems() {
        itemList = new ArrayList<>();
        itemList.add(new OnboardingItem(
                R.drawable.onboarding_1,
                "Welcome to ParkiScan",
                "Your AI-powered companion for early Parkinson’s detection."
        ));
        itemList.add(new OnboardingItem(
                R.drawable.onboarding_2,
                "Track & Improve",
                "Get detailed health insights and connect with experts for better care."
        ));
        itemList.add(new OnboardingItem(
                R.drawable.onboarding_3,
                "Smart Detection",
                "Analyze voice, hand tremor, and facial movement to detect symptoms early."
        ));
    }

    // ✅ Skip or finish onboarding
    private void finishOnboarding() {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        prefs.edit().putBoolean("hasOnboarded", true).apply();

        Intent i = new Intent(OnboardingActivity.this, RoleSelectionActivity.class);
        startActivity(i);
        finish();
    }
}
