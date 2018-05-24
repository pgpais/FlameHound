package pt.unl.fct.di.apdc.firstwebapp.resources;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.dropbox.core.DbxException;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.gson.Gson;
import com.sun.research.ws.wadl.Application;
import pt.unl.fct.di.apdc.firstwebapp.util.Utilities;
import pt.unl.fct.di.apdc.firstwebapp.util.Enums.OccurrencyTypes;
import pt.unl.fct.di.apdc.firstwebapp.util.objects.AuthToken;
import pt.unl.fct.di.apdc.firstwebapp.util.objects.LoginData;
import pt.unl.fct.di.apdc.firstwebapp.util.objects.OccurrencyData;
import pt.unl.fct.di.apdc.firstwebapp.util.objects.SessionInfo;
import pt.unl.fct.di.apdc.firstwebapp.util.objects.UserInfo;

@Path("/occurrency")
public class OccurrencyResource extends HttpServlet{
	
	private static final DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
	private static final Logger LOG = Logger.getLogger(OccurrencyResource.class.getName());
	private final Gson g = new Gson();
	private static DropBoxResource dbIntegration = new DropBoxResource();
	
	public OccurrencyResource() {}
	
	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException , ServletException{
		RequestDispatcher r = request.getRequestDispatcher("pages/occurrencyForm.html");
		r.forward(request, response);
	}
	
	
	@POST
	@Path("/occurrencyByUser/{username}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getOcurrencyByUsername(SessionInfo session, @PathParam("username") String username) {
		if(username == null)
			return Response.status(Status.BAD_REQUEST).build();
		Response r = checkIsLoggedIn(session);
		if(r.getStatus() != Response.Status.OK.getStatusCode())
			return Response.status(Status.FORBIDDEN).build();
			LOG.info("Preparing filter");
			Filter propertyFilter =
			    new FilterPredicate("user", FilterOperator.EQUAL, username);
			LOG.info("Preparing query");
			Query q = new Query("Occurrency").setFilter(propertyFilter);
			LOG.info("Executing query");
			PreparedQuery pQ = datastore.prepare(q);
			LOG.info("Iterator");
			List<Entity> it = pQ.asList(FetchOptions.Builder.withDefaults());
			return Response.ok().entity(g.toJson(it)).build();
	}
	
	@POST
	@Path("/occurrencyByType/{type}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getOcurrencyByType(SessionInfo session, @PathParam("type") String type) {
		if(OccurrencyTypes.valueOf(type) == null)
			return Response.status(Status.BAD_REQUEST).build();
		Response r = checkIsLoggedIn(session);
		if(r.getStatus() != Response.Status.OK.getStatusCode())
			return Response.status(Status.FORBIDDEN).build();
			LOG.info("Preparing filter");
			Filter propertyFilter =
			    new FilterPredicate("type", FilterOperator.EQUAL, type);
			LOG.info("Preparing query");
			Query q = new Query("Occurrency").setFilter(propertyFilter);
			LOG.info("Executing query");
			PreparedQuery pQ = datastore.prepare(q);
			LOG.info("Iterator");
			List<Entity> it = pQ.asList(FetchOptions.Builder.withDefaults());
			return Response.ok().entity(g.toJson(it)).build();
	}
	
	@PUT
	@Path("/updateOccurrency/{ocID}")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response updateOccurrency(SessionInfo session, @PathParam("ocID") String ocID){
		Response r = checkIsLoggedIn(session);
		if(r.getStatus() != Response.Status.OK.getStatusCode())
			return Response.status(Status.FORBIDDEN).build();
		Entity user = (Entity) r.getEntity();
		Transaction txn = datastore.beginTransaction();
		Key ocKey = KeyFactory.createKey("Ocurrency", ocID);
		try {
			LOG.info("Attempt to get ocurrency: " + ocID);
			Entity occurrency = datastore.get(txn, ocKey);
			LOG.info("Got occurrency");
			if(!occurrency.getProperty("user").equals(session.username)) {
				txn.commit();
				Response.status(Status.FORBIDDEN).build();
			}
			
			@SuppressWarnings("unchecked")
			Iterator<String> it = ((List<String>) session.getArgs().get(0)).iterator();
			while(it.hasNext()) {
				String param = it.next();
				String[] line = param.split(":");
				occurrency.setProperty(line[0].trim(), line[1].trim());
			}
			datastore.put(txn, occurrency);
			txn.commit();
			return Response.ok().build();
		}catch (EntityNotFoundException e) {
			LOG.warning("Failed to locate ocurrency: " + ocID);
			return Response.status(Status.NOT_FOUND).build();
		}finally {
			if (txn.isActive()) {
				txn.rollback();
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}
		}
	}
	
	private Response checkIsLoggedIn(SessionInfo session) {
		if(session.tokenId.equals("0")) {
			LOG.warning("User is not logged in");
			return Response.status(Status.FORBIDDEN).build();
		}
		Transaction txn = datastore.beginTransaction();
		Key userKey = KeyFactory.createKey("User", session.username);
		try {
			LOG.info("Attempt to get user: " + session.username);
			Entity user = datastore.get(userKey);
			LOG.info("Got user");
			if(!user.getProperty("TokenKey").equals(session.tokenId)) {
				txn.rollback();
				return Response.status(Status.FORBIDDEN).build();
			}
			LOG.info("Updating timeout");
			txn.commit();
			txn = datastore.beginTransaction();
			Key timeoutKey = KeyFactory.createKey("timeout", session.username);
			Entity timeout = datastore.get(timeoutKey);
			long lastOp = (long) timeout.getProperty("lastOp");
			if(System.currentTimeMillis() - lastOp > 10*60*1000) {
				txn.rollback();
				return Response.status(Status.FORBIDDEN).build();
			}
			timeout.setProperty("lastOp", System.currentTimeMillis());
			datastore.put(timeout);
			LOG.info("User is logged in");
			txn.commit();
			return Response.ok(g.toJson(user)).build();
		}catch (EntityNotFoundException e) {
			LOG.warning("Failed to locate username: " + session.username);
			return Response.status(Status.FORBIDDEN).build();
		} catch(Exception e){
			LOG.warning(e.getMessage());
			return Response.status(Status.FORBIDDEN).build();
		}
			finally {
			if (txn.isActive()) {
				txn.rollback();
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}
		}
		
	}
	
	@POST
	@Path("/saveOccurrency")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response saveOccurrency(OccurrencyData data) {
		Transaction txn = datastore.beginTransaction();
		LOG.info("Generating ID");
		String uuid = Utilities.generateID();
		try {
			LOG.info("Formatting coords");
			
			String aux = data.getLocation();
			LOG.info(aux);
			aux = aux.substring(2, aux.length()-2);
			String[] coords = aux.split(", ");
			LOG.info("Creating occurrency");
			Entity occurrency = new Entity("Occurrency", uuid);
			occurrency.setUnindexedProperty("title", data.getTitle());
			occurrency.setUnindexedProperty("description", data.getDescription());
			occurrency.setIndexedProperty("user", data.getUser());
			
			occurrency.setProperty("locationLat", coords[0].trim());
			occurrency.setProperty("locationLong", coords[1].trim());
			occurrency.setProperty("type", data.getType().toString());
			occurrency.setProperty("creationTime", System.currentTimeMillis());
			occurrency.setProperty("imagesID", data.getMediaURI());
			occurrency.setProperty("confirmed", "no");
			datastore.put(txn, occurrency);
			LOG.info("Put Occurrency");
			
			Transaction txn2 = datastore.beginTransaction();
			Key userStatsKey = KeyFactory.createKey("userAppStats", data.getUser());
			Entity userStatsE = datastore.get(txn, userStatsKey);
			long stat = ((long) userStatsE.getProperty("occurrenciesPosted"));
			userStatsE.setProperty("occurrenciesPosted", ++stat);
			datastore.put(txn2, userStatsE);
			txn.commit();
			txn2.commit();
		}catch (Exception e) {
			LOG.warning(e.getMessage());
		} finally {
			if (txn.isActive()) {
				txn.rollback();
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}
		}
		return Response.ok(g.toJson(uuid)).build();
	}
	
	@POST
	@Path("/saveImage/{extension}")
	@Consumes(MediaType.APPLICATION_OCTET_STREAM)
	@Produces(MediaType.APPLICATION_JSON)
	public Response uploadFileDropbox(byte[] file, @PathParam("extension") String ext) {
		String uuid = Utilities.generateID();
		Transaction txn = datastore.beginTransaction();
		LOG.info("Uploading image");
		try {
			dbIntegration.putFile(uuid, file , ext);
			LOG.info("Uploaded image with id "+uuid);
			txn.commit();
			return Response.ok().build();
	} catch (IOException | DbxException e) {
		return Response.status(Status.INTERNAL_SERVER_ERROR).build();
	} catch (Exception e) {
		return Response.status(Status.INTERNAL_SERVER_ERROR).build();
	}finally {
		if (txn.isActive()) {
			txn.rollback();
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		}
	}
		return Response.ok(g.toJson(uuid+"."+ext)).build();
	}
	
	@POST
	@Path("getImage/{imageID}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	public Response downloadFileDropbox(SessionInfo session, @PathParam("imageID") String imageID){
		Response r = checkIsLoggedIn(session);
		if(r.getStatus() != Response.Status.OK.getStatusCode())
			return r;
		byte[] file;
		try {
			LOG.info("Getting image: " + imageID);
			int point = imageID.indexOf('.');
			String name = imageID.substring(0, point);
			String ext = imageID.substring(name.length()+1);
			file = dbIntegration.getFile(name, ext);
			LOG.info("Found file");
			if(file == null)
				return Response.status(Status.NOT_FOUND).build();
			
		}catch(Exception e) {
			LOG.warning(e.getMessage());
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		}
		return Response.ok(file).build();
	}
	
	@POST
	@Path("getOccurrency/{ocID}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getOccurrency(SessionInfo session, @PathParam("ocID") String ocurrencyID) {
		
		Response r = checkIsLoggedIn(session);
		if(r.getStatus() != Response.Status.OK.getStatusCode())
			return r;
		
		Transaction txn = datastore.beginTransaction();
		if(ocurrencyID.equals("all")) {
			LOG.info("Got general request, querying all occurrencies");
			Query q = new Query("Occurrency");
			PreparedQuery pQ = datastore.prepare(q);
			Iterator<Entity> it = pQ.asIterator();
			LOG.info("Got all");
			List<OccurrencyData> ocList = new ArrayList<OccurrencyData>();
			LOG.info("Converting all to readable format");
			while(it.hasNext()) {
				ocList.add(convertOcToOcData(it.next()));
			}
			return Response.ok(g.toJson(ocList)).build();
		}
		
		Key ocKey = KeyFactory.createKey("Occurrency", ocurrencyID);
		try {
			LOG.info("Attempt to get ocurrency: " + ocurrencyID);
			Entity ocurrency = datastore.get(txn, ocKey);
			LOG.info("Got ocurrency");
			txn.commit();
			LOG.info("Converting occurrency to readable format");
			OccurrencyData oc = convertOcToOcData(ocurrency);
			return Response.ok(g.toJson(oc)).build();
		}catch (EntityNotFoundException e) {
			LOG.warning("Failed to locate ocurrency: " + ocurrencyID);
			txn.rollback();
			return Response.status(Status.NOT_FOUND).build();
		} finally {
			if (txn.isActive()) {
				txn.rollback();
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}
		}
	}

	@SuppressWarnings("unchecked")
	private OccurrencyData convertOcToOcData(Entity ocurrency) {
		String coordinates = ocurrency.getProperty("locationLat") + "," + ocurrency.getProperty("locationLong");
		OccurrencyData oc = new OccurrencyData(
				(String)ocurrency.getProperty("title"),
				(String)ocurrency.getProperty("description"),
				(String)ocurrency.getProperty("user"),
				coordinates,
				(String)ocurrency.getProperty("type"),
				(List<String>) ocurrency.getProperty("imagesID"));
		return oc;
	}
}
