/**
 * 
 */
package com.dynatrace.diagnostics.plugins.mailReport;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Date;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import com.dynatrace.diagnostics.pdk.Action;
import com.dynatrace.diagnostics.pdk.PluginEnvironment;
import com.dynatrace.diagnostics.pdk.Status;

import sun.net.www.protocol.http.AuthCacheImpl;
import sun.net.www.protocol.http.AuthCacheValue;

/**
 * @author cwfr-lizac
 * 
 */
public class MailReport {

	private static final String CONFIG_FROM = "from";
	private static final String CONFIG_TO = "to";
	private static final String CONFIG_SUBJECT = "subject";
	private static final String CONFIG_TEXT = "text";
	private static final String CONFIG_DASHBOARD = "dashboard";
	private static final String CONFIG_HOST = "host";
	private static final String CONFIG_PORT = "port";
	private static final String CONFIG_DTHOST = "dthost";
	private static final String CONFIG_DTPORT = "dtport";
	private static final String CONFIG_DTUSER = "dtuser";
	private static final String CONFIG_DTPWD = "dtpwd";

	private String from;
	private String to;
	private String subject;
	private String text;
	private String dashboard;
	private String host;
	private Long port;
	private String dthost;
	private Long dtport;
	private String dtuser;
	private String dtpwd;

	private static final Logger log = Logger.getLogger(Action.class.getName());

	protected Status setup(PluginEnvironment env) throws Exception {
		Status status =new Status();

		from = env.getConfigString(CONFIG_FROM);
		to = env.getConfigString(CONFIG_TO);
		subject = env.getConfigString(CONFIG_SUBJECT);
		text = env.getConfigString(CONFIG_TEXT);
		dashboard = env.getConfigString(CONFIG_DASHBOARD).replaceAll(" ", "%20");
		host = env.getConfigString(CONFIG_HOST);
		port = env.getConfigLong(CONFIG_PORT);
		dthost = env.getConfigString(CONFIG_DTHOST);
		dtport = env.getConfigLong(CONFIG_DTPORT);
		dtuser = env.getConfigString(CONFIG_DTUSER);
		dtpwd = env.getConfigPassword(CONFIG_DTPWD);

		status.setStatusCode(Status.StatusCode.ErrorTargetServiceExecutionFailed);
		if(from.isEmpty()){
			status.setMessage("Mail sender must not be empty !");	
		} else if(to.isEmpty()){
				status.setMessage("Mail recipient(s) must not be empty !");	
		} else if (dashboard.isEmpty()){
			status.setMessage("Dashboard name must not be empty !");
		} else if (host.isEmpty()){
			status.setMessage("SMTP host name must not be empty !");
		} else if (dthost.isEmpty()){
			status.setMessage("dynaTrace server name must not be empty !");
		} else if (dtuser.isEmpty()){
			status.setMessage("dynaTrace user id must not be empty !");
		} else if (dtpwd.isEmpty()){
			status.setMessage("dynaTrace user password must not be empty !");
		}else{		
			status.setStatusCode(Status.StatusCode.Success);
		}
		return status;
	}

	protected Status execute(PluginEnvironment env) {
		Status status =new Status();
		status.setStatusCode(Status.StatusCode.ErrorTargetServiceExecutionFailed);

		Properties props = System.getProperties();
		props.put("mail.smtp.host", host);
		props.put("mail.smtp.port", port.toString());

		// Get a Session object
		Session session = Session.getInstance(props, null);

		try {

		// create a message
		MimeMessage msg = new MimeMessage(session);
		try {
			msg.setFrom(new InternetAddress(from));
			
			ArrayList recipientsArray=new ArrayList();
			StringTokenizer st = new StringTokenizer(to,";");
			while (st.hasMoreTokens()) {
				recipientsArray.add(st.nextToken());
			}
			int sizeTo=recipientsArray.size();
			InternetAddress[] addressTo = new InternetAddress[sizeTo];
			for (int i = 0; i < sizeTo; i++)
			{
			addressTo[i] = new InternetAddress(recipientsArray.get(i).toString()) ;
			}
			msg.setRecipients(Message.RecipientType.TO, addressTo);		
		} catch (AddressException e) {
			status.setMessage(e.getMessage());
			return status;
		}
		
			msg.setSubject(subject);

		// create and fill the first message part
		MimeBodyPart mbp1 = new MimeBodyPart();
			mbp1.setText(text);

				  
		
		// create the second message part
		MimeBodyPart mbp2 = new MimeBodyPart();

		// attach the file to the message
		String url="http://" 
					+ dthost + ":" + dtport
					+ "/rest/management/reports/create/" + dashboard 
					+ "?type=PDF";
		
		File file=getFileFromUrl(url, dtuser, dtpwd);
		
		log.info("the retrieved file is : " + file.getCanonicalPath());

		// attach the file to the message
			mbp2.attachFile(file.getCanonicalPath());


		// create the Multipart and add its parts to it
		Multipart mp = new MimeMultipart();
			mp.addBodyPart(mbp1);
			mp.addBodyPart(mbp2);

		// add the Multipart to the message
			msg.setContent(mp);

		// set the Date: header
			msg.setSentDate(new Date());

		log.info("sending mail to : " + to);

		// send the message
			Transport.send(msg);
		
		} catch (IOException ioex) {
			status.setMessage(ioex.getMessage());
			return status;
		} catch (MessagingException e) {
			status.setMessage(e.getMessage());
			return status;
		}

		
		status.setStatusCode(Status.StatusCode.Success);

		return status;
	}

	protected void teardown(PluginEnvironment env) throws Exception {
	}

	
    private File getFileFromUrl(String strUrl, final String user, final String pwd)
    {
        File file = null;

    		log.info("contacting URL : " + strUrl);

            URL url;
			try {
				url = new URL(strUrl);

             
			try {
				URLConnection urlConnection = url.openConnection();
				
				// clear cache and hence eliminate problem with allowing non-authorized users to produce dT reports
				// (fix proposed by eugene Turetsky).
				AuthCacheValue.setAuthCache(new AuthCacheImpl());
				
				Authenticator.setDefault(new Authenticator() {

				    @Override
				    protected PasswordAuthentication getPasswordAuthentication() {          
				        return new PasswordAuthentication(user, pwd.toCharArray());
				    }
				});


            String contentType = urlConnection.getContentType();

    		log.info("contentType is : " + contentType);
   		

				file = File.createTempFile("tempReport", ".pdf");
                // If the temp file needs to be deleted upon
                // termination of this application
                file.deleteOnExit();


                
                
                byte[] ba1 = new byte[1024];
                int baLength;
                FileOutputStream fos1 = new FileOutputStream(file);


                  // Checking whether the URL contains a PDF
                  if (!urlConnection.getContentType().equalsIgnoreCase("application/pdf")) {
                	  log.severe("FAILED : Sorry. This is not a PDF.");
                  } else {

                      // Read the PDF from the URL and save to a local file
                      InputStream is1 = url.openStream();
                      while ((baLength = is1.read(ba1)) != -1) {
                          fos1.write(ba1, 0, baLength);
                      }
                      fos1.flush();
                      fos1.close();
                      is1.close();
                  }		  
						  
						  
			} catch (IOException e) {
				log.severe(e.getMessage());
			}
			} catch (MalformedURLException e) {
				log.severe(e.getMessage());
			}
        

        return file;
    }
    
}
