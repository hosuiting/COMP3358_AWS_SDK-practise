import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.List;
import java.util.UUID;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.UUID;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.AmazonSQSException;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageRequest;

public class COMP3358A4Server {
	public static void main(String[] args) {
		
		AWSCredentials credentials = null;
		try {
			credentials = new ProfileCredentialsProvider("default").getCredentials();
	    } catch (Exception e) {
	            throw new AmazonClientException(
	                    "Cannot load the credentials from the credential profiles file. " +
	                    "Please make sure that your credentials file is at the correct " +
	                    "location (C:\\Users\\hosuiting\\.aws\\credentials), and is in valid format.",
	                    e);
	    }
		
        System.out.println("===========================================");
        System.out.println("Getting Started with Server");
        System.out.println("===========================================\n");
        
        AmazonSQS sqs = AmazonSQSClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion("ap-east-1")
                .build();
        AmazonS3 s3 = AmazonS3ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion("ap-east-1")
                .build();
        
        String QUEUE_NAME_INBOX = "COMP3358A4SQS-inbox";
        String QUEUE_NAME_OUTBOX = "COMP3358A4SQS-outbox";
        String bucketName = "comp3358a4-3035569330";
        String userDirectory = System.getProperty("user.dir");
        String inbox_queue_url = sqs.getQueueUrl(QUEUE_NAME_INBOX).getQueueUrl();
        
        while(true) {
        	//getting message from inbox_queue
        	System.out.println("Reading message from the inbox queue");
        	String key;
        	List<Message> messages = sqs.receiveMessage(new ReceiveMessageRequest(inbox_queue_url).withMaxNumberOfMessages(1)).getMessages();
        	if (messages.size()>0) {
        		Message msg = messages.get(0);
        		key = msg.getBody();
				String filename = key + ".png";
				try {
					S3Object o = s3.getObject(bucketName, key);
					S3ObjectInputStream s3is = o.getObjectContent();
					//System.out.println(userDirectory);
					System.out.println("Creating the file in local\n");
					//System.out.println("userDirectory\n");
					FileOutputStream fos = new FileOutputStream(new File(filename));
					byte[] read_buf = new byte[1024];
					int read_len = 0;
					while ((read_len = s3is.read(read_buf)) > 0) {
						//System.out.println("true");
						fos.write(read_buf, 0, read_len);
					}
					s3is.close();
					fos.close();
					System.out.println("Downloading an image from S3\n");
				} catch (AmazonServiceException e) {
					System.err.println(e.getErrorMessage());
					System.exit(1);
				} catch (FileNotFoundException e) {
					System.err.println(e.getMessage());
					System.exit(1);
				} catch (IOException e) {
					System.err.println(e.getMessage());
					System.exit(1);
				}
				
				//Delete the original image from S3
				System.out.println("Deleting the original image from S3\n");
				s3.deleteObject(bucketName, key);
	        
				/////Process the image here
				System.out.println("Processing the image \n");
				String outputFileName = key + "45.png";
				System.out.println(key);
				String Command = "convert " + filename + " -rotate 45 " + outputFileName;
				try {
					Runtime rt = Runtime.getRuntime();
					//Process pr = rt.exec("convert test.png -rotate 45 test45.png");
					Process pr = rt.exec(Command);
					System.out.println("Processed the image \n");
					//sleep two seconds to wait the file processed
					Thread.sleep(2000);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				/////

	        
				//Uploading the processed image
				try {
					s3.putObject(bucketName, key, new File(userDirectory + "/" + outputFileName));
					System.out.println("Uploading the processed image to S3");
				} catch (AmazonServiceException e) {
					System.err.println(e.getErrorMessage());
					System.exit(1);
				}
	        
				// Sending message to the output queue
				String outbox_queue_url = sqs.getQueueUrl(QUEUE_NAME_OUTBOX).getQueueUrl();
				SendMessageRequest send_msg_request = new SendMessageRequest()
						.withQueueUrl(outbox_queue_url)
						.withMessageBody(key)
						.withDelaySeconds(5);
				sqs.sendMessage(send_msg_request);
				System.out.println("Send message to outbox queue");
	        
				// inbox message handled,delete the message
				sqs.deleteMessage(inbox_queue_url, msg.getReceiptHandle());
				
        	}else {
        		System.out.println("No message in Inbox queue, sleep for 30 seconds");
        		try {
					Thread.sleep(30000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
        	}
        }
	}
}
