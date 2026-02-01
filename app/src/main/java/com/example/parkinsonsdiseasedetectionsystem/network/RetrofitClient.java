package com.example.parkinsonsdiseasedetectionsystem.network;

import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {

    // ✅ YOUR NEW CLOUD SERVER URL (Note the slash at the end!)
    private static final String BASE_URL = "https://parkinson-api-4so8.onrender.com/";

    private static Retrofit retrofit = null;

    public static ParkinsonApiService getService() {
        if (retrofit == null) {
            // Increase timeout to 60s because Cloud servers can be slow on the first try
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit.create(ParkinsonApiService.class);
    }
}