package com.example.myapplication;

import com.clarifai.credentials.ClarifaiCallCredentials;
import com.clarifai.grpc.api.Concept;
import com.clarifai.grpc.api.Data;
import com.clarifai.grpc.api.Image;
import com.clarifai.grpc.api.Input;
import com.clarifai.grpc.api.MultiOutputResponse;
import com.clarifai.grpc.api.PostModelOutputsRequest;
import com.clarifai.grpc.api.UserAppIDSet;
import com.clarifai.grpc.api.V2Grpc;
import com.clarifai.grpc.api.status.StatusCode;
import com.google.protobuf.ByteString;

import net.suuft.libretranslate.Translator;

import io.grpc.ManagedChannel;
import io.grpc.okhttp.OkHttpChannelBuilder;


public class ClarifaiApiClient {

    private static final String PAT = "eea9f30ee77847a7a74b2ca886f85888";  // Replace with your actual PAT
    private static final String USER_ID = "tac-tcc";  // Replace with your actual user_id
    private static final String APP_ID = "TAC";  // Replace with your actual app_id
    private static final String MODEL_ID = "general-image-recognition";  // Replace with the model you want to use
    private static final String MODEL_VERSION_ID = "aa7f35c01e0642fda5cf400f543e7c40";  // Optional: replace with specific version if needed
    static final String IMAGE_URL = "https://samples.clarifai.com/metro-north.jpg";

    private final V2Grpc.V2BlockingStub stub;

    public ClarifaiApiClient() {
        // Initialize the gRPC channel using OkHttp
        ManagedChannel channel = OkHttpChannelBuilder
                .forAddress("api.clarifai.com", 443)
                .useTransportSecurity()
                .build();

        // Create the gRPC stub with the API key credentials
        this.stub = V2Grpc.newBlockingStub(channel)
                .withCallCredentials(new ClarifaiCallCredentials(PAT));
    }

    public static String capitalizeFirstCharacter(String input) {
        if (input == null || input.isEmpty()) {
            return input; // Return the input as is if it's null or empty
        }
        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }

    public String classifyImage(byte[] imageBytes) throws Exception {
        // Convert image byte array to Base64
        // Create the request to process the image
        PostModelOutputsRequest request = PostModelOutputsRequest.newBuilder()
                .setUserAppId(UserAppIDSet.newBuilder().setUserId(USER_ID).setAppId(APP_ID))
                .setModelId(MODEL_ID)
                .addInputs(
                        Input.newBuilder().setData(
                                Data.newBuilder().setImage(
                                        Image.newBuilder().setBase64(ByteString.copyFrom(imageBytes))
                                )
                        )
                )
                .build();

        // Send the request to Clarifai API and handle the response
        MultiOutputResponse response = stub.postModelOutputs(request);
        // Check if the request was successful
        if (response.getStatus().getCode() != StatusCode.SUCCESS) {
            throw new RuntimeException("Request failed, status: " + response.getStatus());
        }

        // Find the concept with the highest confidence
        Concept highestConcept = null;
        float highestConfidence = -1;
        for (Concept concept : response.getOutputs(0).getData().getConceptsList()) {
            if (concept.getValue() > highestConfidence) {
                highestConfidence = concept.getValue();
                highestConcept = concept;
            }
        }

        // Return the highest confidence concept name
        if (highestConcept != null) {
            return capitalizeFirstCharacter(Translator.translate("en", "pt", highestConcept.getName()));
        } else {
            return "Objeto desconhecido";  // If no concept found
        }
    }
}
