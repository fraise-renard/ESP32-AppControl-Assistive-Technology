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

    //if one of the concepts is person or face, priorize it, if it's central.
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

        // Variables to track the best bounding box based on centrality and size
        Concept mainConcept = null;
        float bestScore = Float.MAX_VALUE; // Lower is better for distance and area combination

        for (Region region : response.getOutputs(0).getData().getRegionsList()) {
            BoundingBox bbox = region.getRegionInfo().getBoundingBox();

            // Calculate the center of the bounding box
            float bboxCenterX = (bbox.getLeftCol() + bbox.getRightCol()) / 2;
            float bboxCenterY = (bbox.getTopRow() + bbox.getBottomRow()) / 2;

            // Calculate the area of the bounding box
            float bboxWidth = bbox.getRightCol() - bbox.getLeftCol();
            float bboxHeight = bbox.getBottomRow() - bbox.getTopRow();
            float bboxArea = bboxWidth * bboxHeight;

            // Calculate distance from the image center (normalized coordinates)
            float distanceToCenter = (float) Math.sqrt(
                    Math.pow(0.5 - bboxCenterX, 2) + Math.pow(0.5 - bboxCenterY, 2)
            );

            // Calculate a score: prioritize small distance and large area
            // A smaller score is better. Here we use (distance / bboxArea) to weigh the area higher.
            float score = distanceToCenter / bboxArea;

            // Update the main concept if this box has a better score
            if (score < bestScore) {
                bestScore = score;
                float highestValue = 0;
                for(Concept c : region.getData().getConceptsList()){
                    if(c.getValue() > highestValue){
                        highestValue = c.getValue();
                        mainConcept = c;
                    }
                }
            }
        }

        if (mainConcept != null) {
            return capitalizeFirstCharacter(Translator.translate("en", "pt", mainConcept.getName()));
        } else {
            return "Objeto principal desconhecido";
        }
    }

    private static String capitalizeFirstCharacter(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }
}

