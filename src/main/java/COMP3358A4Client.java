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
import com.amazonaws.services.sqs.model.SendMessageRequest;

public class COMP3358A4Client {
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
        System.out.println("Getting Started with Client");
        System.out.println("===========================================\n");        
        
		//Create S3 client to handle the tasks
        AmazonS3 s3 = AmazonS3ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion("ap-east-1")
                .build();
        AmazonSQS sqs = AmazonSQSClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion("ap-east-1")
                .build();
        
        String QUEUE_NAME_INBOX = "COMP3358A4SQS-inbox";
        String QUEUE_NAME_OUTBOX = "COMP3358A4SQS-outbox";
        String bucketName = "comp3358a4-3035569330";
        String inbox_queue_url = sqs.getQueueUrl(QUEUE_NAME_INBOX).getQueueUrl();
        String outbox_queue_url = sqs.getQueueUrl(QUEUE_NAME_OUTBOX).getQueueUrl();
        
        
        //Create a bucket call comp3358a4-3035569330
        if (s3.doesBucketExistV2(bucketName)) {
            System.out.format("Bucket %s already exists.\n", bucketName);
        } else {
            try {
            	s3.createBucket(bucketName);
            	System.out.println("Successfully created the bucket" + bucketName + "\n" );
            } catch (AmazonS3Exception e) {
                System.err.println(e.getErrorMessage());
            }
        }
        //list all buckets in my account
        System.out.println("Listing buckets");
        for (Bucket bucket : s3.listBuckets()) {
            System.out.println(" - " + bucket.getName());
        }
        
        //upload the picture to the S3
        String key = "myPhoto";
        try {
            s3.putObject(bucketName, key, new File("C:\\Users\\hosuiting\\Desktop\\COMP3358A4\\test.png"));
            System.out.println("Uploading a new object to S3 from a file\n");
        } catch (AmazonServiceException e) {
            System.err.println(e.getErrorMessage());
            System.exit(1);
        }
        
        //download the picture from S3
        /*
        try {
            S3Object o = s3.getObject(bucketName, key);
            S3ObjectInputStream s3is = o.getObjectContent();
            FileOutputStream fos = new FileOutputStream(new File("C:\\Users\\hosuiting\\Desktop\\test2.png"));
            byte[] read_buf = new byte[1024];
            int read_len = 0;
            while ((read_len = s3is.read(read_buf)) > 0) {
                fos.write(read_buf, 0, read_len);
            }
            s3is.close();
            fos.close();
            System.out.println("Downloading an object\n");
        } catch (AmazonServiceException e) {
            System.err.println(e.getErrorMessage());
            System.exit(1);
        } catch (FileNotFoundException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }*/
        
        //Create two new SQS queue on AWS one for inbox, one for outbox
        CreateQueueRequest create_request = new CreateQueueRequest(QUEUE_NAME_INBOX)
                .addAttributesEntry("DelaySeconds", "60")
                .addAttributesEntry("MessageRetentionPeriod", "86400");
        
        CreateQueueRequest create_request2 = new CreateQueueRequest(QUEUE_NAME_OUTBOX)
                .addAttributesEntry("DelaySeconds", "60")
                .addAttributesEntry("MessageRetentionPeriod", "86400");

        try {
            sqs.createQueue(create_request);
            sqs.createQueue(create_request2);
            System.out.println("COMP3358A4SQS Queue Created successfully");
        } catch (AmazonSQSException e) {
            if (!e.getErrorCode().equals("QueueAlreadyExists")) {
            	System.out.println("COMP3358A4SQS Queue AlreadyExists");
                throw e;
            }
        }
        //printing the Queue in my account
        ListQueuesResult lq_result = sqs.listQueues();
        System.out.println("Your SQS Queue URLs:");
        for (String url : lq_result.getQueueUrls()) {
            System.out.println(url);
        }
        
        //sending message to inbox queue and place the S3 key inside
        SendMessageRequest send_msg_request = new SendMessageRequest()
                .withQueueUrl(inbox_queue_url)
                .withMessageBody(key)
                .withDelaySeconds(5);
        sqs.sendMessage(send_msg_request);
        System.out.println("Send message to inbox queue");
        
        //waiting for message from outbox queue
        boolean processed = false;
        int count = 0;
        //we stop when the image is handled or timeout after 60seconds
        while(!processed && count <3) {
        	System.out.println("Reading message from the outbox queue");
        	List<Message> messages = sqs.receiveMessage(outbox_queue_url).getMessages();
        	if (messages.size()>0) {
        		for (Message m : messages) {
        			String output_key = m.getBody();
        			if(output_key.equals(key)) {
        				System.out.println("Found match key in outbox queue, start downloading");
        		        try {
        		            S3Object o = s3.getObject(bucketName, key);
        		            S3ObjectInputStream s3is = o.getObjectContent();
        		            FileOutputStream fos = new FileOutputStream(new File("C:\\Users\\hosuiting\\Desktop\\COMP3358A4\\test2.png"));
        		            byte[] read_buf = new byte[1024];
        		            int read_len = 0;
        		            while ((read_len = s3is.read(read_buf)) > 0) {
        		                fos.write(read_buf, 0, read_len);
        		            }
        		            s3is.close();
        		            fos.close();
        		            System.out.println("Downloading an object\n");
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
        		        
        				//Delete the image from S3
        				System.out.println("Deleting the original image from S3\n");
        				s3.deleteObject(bucketName, key);
        				
        				//Handle the message, delete it from outbox queue
        				System.out.println("Handled the message. Deleting the message from outbox queue\n");
        				sqs.deleteMessage(outbox_queue_url, m.getReceiptHandle());
        				return;
        			}
        		}
        		System.out.println("No matched message in outbox queue, sleep 20 seconds\n");
        		count ++;
        		try {
					Thread.sleep(20000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
        	}else {
        		System.out.println("No message in outbox queue yet, sleep 20 seconds\n");
        		count ++;
        		try {
					Thread.sleep(20000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
        	}
        }
        
        
        
        
        
        
        
        
        ///////delete the queue///////
        /*
        String queue_url = sqs.getQueueUrl(QUEUE_NAME).getQueueUrl();
        sqs.deleteQueue(queue_url);
        */
        
        //Delete the object from S3
        // System.out.println("Deleting an object\n");
        //s3.deleteObject(bucketName, key);
	}
}
