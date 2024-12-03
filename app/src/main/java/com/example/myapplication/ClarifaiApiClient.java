package com.example.myapplication;

import com.clarifai.credentials.ClarifaiCallCredentials;
import com.clarifai.grpc.api.BoundingBox;
import com.clarifai.grpc.api.Concept;
import com.clarifai.grpc.api.Data;
import com.clarifai.grpc.api.Image;
import com.clarifai.grpc.api.Input;
import com.clarifai.grpc.api.MultiOutputResponse;
import com.clarifai.grpc.api.PostModelOutputsRequest;
import com.clarifai.grpc.api.Region;
import com.clarifai.grpc.api.UserAppIDSet;
import com.clarifai.grpc.api.V2Grpc;
import com.clarifai.grpc.api.status.StatusCode;
import com.google.protobuf.ByteString;

import net.suuft.libretranslate.Translator;

import io.grpc.ManagedChannel;
import io.grpc.okhttp.OkHttpChannelBuilder;

public class ClarifaiApiClient {

    private static final String PAT = "eea9f30ee77847a7a74b2ca886f85888"; // Replace with your actual PAT
    private static final String USER_ID = "tac-tcc"; // Replace with your user ID
    private static final String APP_ID = "TAC"; // Replace with your app ID
    private static final String MODEL_ID = "general-image-detection"; // Object detection model ID

    private final V2Grpc.V2BlockingStub stub;

    public ClarifaiApiClient() {
        ManagedChannel channel = OkHttpChannelBuilder
                .forAddress("api.clarifai.com", 443)
                .useTransportSecurity()
                .build();

        this.stub = V2Grpc.newBlockingStub(channel)
                .withCallCredentials(new ClarifaiCallCredentials(PAT));
    }

    public String detectCentralObject(byte[] imageBytes) throws Exception {
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

        MultiOutputResponse response = stub.postModelOutputs(request);
        if (response.getStatus().getCode() != StatusCode.SUCCESS) {
            throw new RuntimeException("Request failed, status: " + response.getStatus());
        }

        // Variables to track the most central bounding box
        Concept centralConcept = null;
        float smallestDistanceToCenter = Float.MAX_VALUE;

        for (Region region : response.getOutputs(0).getData().getRegionsList()) {
            BoundingBox bbox = region.getRegionInfo().getBoundingBox();
            Concept concept = region.getData().getConcepts(0); // Assume the first concept is the main one

            // Calculate the center of the bounding box using getLeftCol, getRightCol, getTopRow, getBottomRow
            float bboxCenterX = (bbox.getLeftCol() + bbox.getRightCol()) / 2;
            float bboxCenterY = (bbox.getTopRow() + bbox.getBottomRow()) / 2;

            // Calculate distance from image center (normalized coordinates)
            //Euclidean formula of distance
            //0.5,0.5 is always the center of the image
            float distanceToCenter = (float) Math.sqrt(
                    Math.pow(0.5 - bboxCenterX, 2) + Math.pow(0.5 - bboxCenterY, 2)
            );

            // Update the most central concept if this one is closer to the center
            if (distanceToCenter < smallestDistanceToCenter) {
                smallestDistanceToCenter = distanceToCenter;
                centralConcept = concept;
            }
        }

        if (centralConcept != null) {
            return capitalizeFirstCharacter(Translator.translate("en", "pt", centralConcept.getName()));
        } else {
            return "Objeto central desconhecido";
        }
    }

    private static String capitalizeFirstCharacter(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }
}

