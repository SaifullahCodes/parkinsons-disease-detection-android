package com.example.parkinsonsdiseasedetectionsystem.network;


import okhttp3.MultipartBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public interface ParkinsonApiService {
    @Multipart
    @POST("predict/")
    Call<ResponseBody> uploadAudio(@Part MultipartBody.Part file);
}