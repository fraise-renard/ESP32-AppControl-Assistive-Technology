package com.example.myapplication;

import com.clarifai.credentials.ClarifaiCallCredentials;
import com.clarifai.grpc.api.BoundingBox;
import com.clarifai.grpc.api.Concept;
import com.clarifai.grpc.api.Data;
import com.clarifai.grpc.api.Image;
import com.clarifai.grpc.api.Input;
import com.clarifai.grpc.api.Output;
import com.clarifai.grpc.api.PostWorkflowResultsRequest;
import com.clarifai.grpc.api.PostWorkflowResultsResponse;
import com.clarifai.grpc.api.Region;
import com.clarifai.grpc.api.UserAppIDSet;
import com.clarifai.grpc.api.V2Grpc;
import com.clarifai.grpc.api.WorkflowResult;
import com.clarifai.grpc.api.status.StatusCode;
import com.google.protobuf.ByteString;

import net.suuft.libretranslate.Language;
import net.suuft.libretranslate.Translator;

import java.util.HashSet;
import java.util.Set;

import io.grpc.ManagedChannel;
import io.grpc.okhttp.OkHttpChannelBuilder;

public class ClarifaiApiClient {

    private static final String PAT = "eea9f30ee77847a7a74b2ca886f85888"; // Replace with your actual PAT
    private static final String USER_ID = "tac-tcc"; // Replace with your user ID
    private static final String APP_ID = "TAC"; // Replace with your app ID
    private static final String WORKFLOW_ID = "interpreter"; // Object detection model ID
    // Priority object concepts
    private static final Set<String> PRIORITY_OBJECTS = new HashSet<>();
    private final V2Grpc.V2BlockingStub stub;

    public ClarifaiApiClient() {
        PRIORITY_OBJECTS.add("person");
        PRIORITY_OBJECTS.add("man");
        PRIORITY_OBJECTS.add("woman");
        PRIORITY_OBJECTS.add("human");
        PRIORITY_OBJECTS.add("face");
        PRIORITY_OBJECTS.add("car");

        ManagedChannel channel = OkHttpChannelBuilder
                .forAddress("api.clarifai.com", 443)
                .useTransportSecurity()
                .build();

        this.stub = V2Grpc.newBlockingStub(channel)
                .withCallCredentials(new ClarifaiCallCredentials(PAT));
    }

    //if one of the concepts is person or face, priorize it, if it's central.
    public String detectCentralObject(byte[] imageBytes) throws Exception {
        PostWorkflowResultsResponse request = stub.postWorkflowResults(
                PostWorkflowResultsRequest.newBuilder()
                .setUserAppId(UserAppIDSet.newBuilder().setUserId(USER_ID).setAppId(APP_ID))
                .setWorkflowId(WORKFLOW_ID)
                .addInputs(
                        Input.newBuilder().setData(
                                Data.newBuilder().setImage(
                                        Image.newBuilder().setBase64(ByteString.copyFrom(imageBytes))
                                )
                        )
                )
                .build());

        if (request.getStatus().getCode() != StatusCode.SUCCESS) {
            throw new RuntimeException("Request failed, status: " + request.getStatus());
        }
        // Process the workflow results
        WorkflowResult result = request.getResults(0);
        // Variables for text and object selection
        String bestText = null;
        float bestTextScore = Float.MAX_VALUE;

        Region bestRegion = null;
        float bestObjectScore = Float.MAX_VALUE;

        // Iterate through outputs for each model
        for (Output output : result.getOutputsList()) {
            String modelId = output.getModel().getId();

            for (Region region : output.getData().getRegionsList()) {
                BoundingBox bbox = region.getRegionInfo().getBoundingBox();
                float bboxCenterX = (bbox.getLeftCol() + bbox.getRightCol()) / 2;
                float bboxCenterY = (bbox.getTopRow() + bbox.getBottomRow()) / 2;
                float bboxArea = (bbox.getRightCol() - bbox.getLeftCol()) * (bbox.getBottomRow() - bbox.getTopRow());
                float distanceToCenter = (float) Math.sqrt(Math.pow(0.5 - bboxCenterX, 2) + Math.pow(0.5 - bboxCenterY, 2));
                float score = distanceToCenter / bboxArea; // Smaller score is better

                if (modelId.equals("general-image-detection")) {
                    if (score < bestObjectScore) { // Confidence threshold for objects
                        bestObjectScore = score;
                        //sets the most centralized and biggest object region
                        bestRegion = region;
                    }
                }else{
                    if (score < bestTextScore) {
                        bestTextScore = score;
                        bestText = region.getData().getText().getRaw();
                    }
                }
            }
        }

        Concept bestConcept = null;
        if(bestRegion != null) {
            for (Concept concept : bestRegion.getData().getConceptsList()) {
                if (PRIORITY_OBJECTS.contains(concept.getName())) {
                    bestConcept = concept; //major concept filtering mecanism
                    break;
                }else if (concept.getValue() > 0.5) {
                    bestConcept = concept;
                }
            }
        }

        // Decision-making logic
        if (bestTextScore < bestObjectScore && bestText != null && !bestText.isEmpty() && !bestText.equals(" ")) {
            return "Texto detectado: '" + bestText + "'";
        } else if (bestConcept != null) {
            return capitalizeFirstCharacter(Translator.translate(Language.PORTUGUESE,bestConcept.getName()));
        } else {
            return "Objeto desconhecido";
        }

    }


    private static String capitalizeFirstCharacter(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }
}

