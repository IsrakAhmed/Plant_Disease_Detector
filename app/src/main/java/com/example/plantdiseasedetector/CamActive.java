package com.example.plantdiseasedetector;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.example.plantdiseasedetector.ml.DiseaseDetection;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;


public class CamActive extends AppCompatActivity {

    TextView result, demoTxt, classified, clickHere, accuracy;
    ImageView imageView;
    Button picture,gallary;
    int imageSize = 224;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cam_active);


        result = findViewById(R.id.result);
        accuracy = findViewById(R.id.accuracy);
        imageView = findViewById(R.id.imageView);
        picture = findViewById(R.id.button);
        gallary = findViewById(R.id.gallary);

        demoTxt = findViewById(R.id.demoText);
        classified = findViewById(R.id.classified);
        clickHere = findViewById(R.id.click_here);

        demoTxt.setVisibility(View.VISIBLE);
        clickHere.setVisibility(View.GONE);
        classified.setVisibility(View.GONE);
        result.setVisibility(View.GONE);
        accuracy.setVisibility(View.GONE);


        picture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED){
                    Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    startActivityForResult(cameraIntent,1);
                }
                else{
                    //request camera permission
                    requestPermissions(new String[]{Manifest.permission.CAMERA},100);
                }
            }
        });
    }


    public void gallaryClick(View v) {
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(galleryIntent, 2);
        } else {
            // Request permission to access external storage
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 200);
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == 1 && resultCode == RESULT_OK){
            Bitmap image = (Bitmap) data.getExtras().get("data");
            int dimension = Math.min(image.getWidth(),image.getHeight());
            image = ThumbnailUtils.extractThumbnail(image,dimension,dimension);


            imageView.setImageBitmap(image);

            demoTxt.setVisibility(View.GONE);
            clickHere.setVisibility(View.VISIBLE);
            classified.setVisibility(View.VISIBLE);
            result.setVisibility(View.VISIBLE);
            accuracy.setVisibility(View.VISIBLE);

            image = Bitmap.createScaledBitmap(image,imageSize,imageSize,false);
            classifyImage(image);
        }

        else if (requestCode == 2 && resultCode == RESULT_OK && data != null) {
            Uri selectedImageUri = data.getData();
            try {
                Bitmap image = MediaStore.Images.Media.getBitmap(this.getContentResolver(), selectedImageUri);
                int dimension = Math.min(image.getWidth(),image.getHeight());
                image = ThumbnailUtils.extractThumbnail(image,dimension,dimension);


                imageView.setImageBitmap(image);

                demoTxt.setVisibility(View.GONE);
                clickHere.setVisibility(View.VISIBLE);
                classified.setVisibility(View.VISIBLE);
                result.setVisibility(View.VISIBLE);
                accuracy.setVisibility(View.VISIBLE);

                image = Bitmap.createScaledBitmap(image,imageSize,imageSize,false);
                classifyImage(image);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    private void classifyImage(Bitmap image) {
        try {
            DiseaseDetection model = DiseaseDetection.newInstance(getApplicationContext());

            // Creates inputs for reference.
            TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 224,224, 3}, DataType.FLOAT32);
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * 224 * 224 * 3);
            byteBuffer.order(ByteOrder.nativeOrder());

            //get 1d array of 224*224 pixels in image
            int[] intValue = new int[imageSize*imageSize];
            image.getPixels(intValue,0,image.getWidth() ,0,0,image.getWidth(),image.getHeight());

            // Iterate Over pixel and extract R,G,B value , add to bytebuffer
            int pixel = 0;
            for (int i = 0; i<imageSize;i++){
                for (int j=0;j<imageSize;j++){
                    int val = intValue[pixel++];//RGB
                    byteBuffer.putFloat(((val >> 16)& 0xFF)*(1.f / 255.f));
                    byteBuffer.putFloat(((val >> 8)& 0xFF)*(1.f / 255.f));
                    byteBuffer.putFloat((val & 0xFF)*(1.f / 255.f));
                }
            }
            inputFeature0.loadBuffer(byteBuffer);
            //run model interface
            DiseaseDetection.Outputs outputs = model.process(inputFeature0);
            TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();

            float[] confidence = outputFeature0.getFloatArray();
            //find index of class with biggest confidence
            int maxPos = 0;
            float maxConfidence = 0;
            for (int i = 0;i<confidence.length;i++){
                if (confidence[i] > maxConfidence){
                    maxConfidence = confidence[i];
                    maxPos = i;
                }
            }

            String[] classes = {"Pepper Bell Bacterial Spot",
                    "Healthy Pepper Bell","Potato Early Blight","Healthy Potato",
                    "Potato Late Blight","Tomato Target Spot","Tomato Mosaic Virus",
                    "Tomato Yellow Leaf Curl Virus","Tomato Bacterial Spot","Tomato Early Blight",
                    "Healthy Tomato","Tomato Late Blight","Tomato Leaf Mold",
                    "Tomato Septori Leaf Spot","Tomato Spider Mites"};

            result.setText(classes[maxPos]);

            float totalConfidence = 0.0f;

            // Calculate the total confidence across all classes
            for (float conf : confidence) {
                totalConfidence += conf;
            }

            // Calculate accuracy percentage
            float accuracyPercentage = (maxConfidence / totalConfidence) * 100;

            // Display the accuracy percentage
            String accuracyText = String.format("Accuracy: %.2f%%", accuracyPercentage);


            accuracy.setText(accuracyText);
            result.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    //to search disease on internet
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q="+result.getText())));
                }
            });

            model.close();
        }catch (IOException e){
            Toast.makeText(this, "Network Error", Toast.LENGTH_SHORT).show();
        }
    }
}