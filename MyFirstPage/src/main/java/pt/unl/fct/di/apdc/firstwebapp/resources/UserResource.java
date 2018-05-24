package pt.unl.fct.di.apdc.firstwebapp.resources;

import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.PropertyProjection;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.api.datastore.Query.SortDirection;
import com.google.api.client.util.store.DataStore;
import org.apache.commons.codec.digest.DigestUtils;
import com.google.gson.Gson;

import pt.unl.fct.di.apdc.firstwebapp.util.Enums.UserRoles;
import pt.unl.fct.di.apdc.firstwebapp.util.objects.AdminInfo;
import pt.unl.fct.di.apdc.firstwebapp.util.objects.AdminRegisterInfo;
import pt.unl.fct.di.apdc.firstwebapp.util.objects.AuthToken;
import pt.unl.fct.di.apdc.firstwebapp.util.objects.LoginData;
import pt.unl.fct.di.apdc.firstwebapp.util.objects.SessionInfo;
import pt.unl.fct.di.apdc.firstwebapp.util.objects.UserStatsData;

@Path("/user")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class UserResource extends HttpServlet {

	/**
	 * 
	 */
	/**
	 * A logger object.
	 */
	private static final Logger LOG = Logger.getLogger(UserResource.class.getName());
	private final Gson g = new Gson();
	private static final DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

	public UserResource() {
	} // Nothing to be done here...

	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException , ServletException{
		RequestDispatcher r = request.getRequestDispatcher("pages/login.html");
		r.forward(request, response);
	}
	
	public Response validLogin(SessionInfo session) {
		if (session.tokenId.equals("0")) {
			LOG.warning("User is not logged in");
			return Response.status(Status.FORBIDDEN).build();
		}
		Transaction txn = datastore.beginTransaction();
		Transaction txn2 = datastore.beginTransaction();
		Key userKey = KeyFactory.createKey("User", session.username);
		try {
			LOG.info("Attempt to get user: " + session.username);
			Entity user = datastore.get(txn, userKey);
			LOG.info("Got user");
			if (!user.getProperty("TokenKey").equals(session.tokenId)) {
				LOG.info("Wrong token for user " + session.username);
				txn.commit();
				txn2.commit();
				return Response.status(Status.FORBIDDEN).build();
			}
			LOG.info("Correct token for use " + session.username);

			Key timeoutKey = KeyFactory.createKey("timeout", session.username);
			LOG.info("Got timeoutKey");
			Entity timeout = datastore.get(txn2, timeoutKey);
			LOG.info("Got Timeout");
			long lastOp = (long) timeout.getProperty("lastOp");
			LOG.info("timeout is Long");
			if (System.currentTimeMillis() - lastOp > 10 * 60 * 1000) {
				LOG.info("Timed out");
				user.setProperty("TokenExpirationDate", "");
				user.setProperty("TokenCreationDate", "");
				user.setProperty("TokenKey", 0);
				datastore.put(txn, user);
				txn.commit();
				txn2.commit();
				return Response.status(Status.FORBIDDEN).build();
			}
			LOG.info("Didn't time out");
			timeout.setProperty("lastOp", System.currentTimeMillis());
			datastore.put(txn2, timeout);
			txn.commit();
			txn2.commit();
			return Response.ok(user).build();
		} catch (EntityNotFoundException e) {
			LOG.warning("Failed to locate username: " + session.username);
			txn.rollback();
			txn2.rollback();
			return Response.status(Status.FORBIDDEN).build();
		} catch(Exception e) {
			LOG.info(e.getMessage());
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		}
		finally {
			if (txn.isActive() || txn2.isActive()) {
				LOG.info("Transactions still active");
				txn.rollback();
				txn2.rollback();
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}
		}

	}
	
	@GET
	@Path("/getStats")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getUserStatistics(SessionInfo session) {
		Response r = validLogin(session);
		if(r.getStatus() != Response.Status.OK.getStatusCode())
			return Response.status(Status.FORBIDDEN).build();
		Key userStatsKey = KeyFactory.createKey("userAppStats", session.username);
		Transaction txn = datastore.beginTransaction();
		try {
			Entity userStatsE = datastore.get(txn, userStatsKey);
			UserStatsData userStats = new UserStatsData(
					(long) userStatsE.getProperty("upvotes"),
					(long) userStatsE.getProperty("downvotes"),
					(long) userStatsE.getProperty("occurrenciesPosted"),
					(long) userStatsE.getProperty("occurrenciesConfirmed")
					);
			return Response.ok().entity(g.toJson(userStats)).build();
		} catch (EntityNotFoundException e) {
			LOG.warning("Could not find stats for user: " + session.username);
			return Response.status(Status.NOT_FOUND).build();
		}finally {
			if(txn.isActive()) {
				txn.rollback();
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}
		}
		
		
	}
	
	@POST
	@Path("/updateProfile")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response updateProfile(SessionInfo session){
		Response r = validLogin(session);
		if(r.getStatus() != Response.Status.OK.getStatusCode())
			return Response.status(Status.FORBIDDEN).build();
		Entity user = (Entity) r.getEntity();
		Transaction txn = datastore.beginTransaction();
		@SuppressWarnings("unchecked")
		Iterator<String> it = ((List<String>) session.getArgs().get(0)).iterator();
		while(it.hasNext()) {
			String param = it.next();
			String[] line = param.split(":");
			LOG.info("Updating parameter " + line[0].trim() + " with value " + line[1].trim());
			user.setProperty(line[0].trim(), line[1].trim());
		}
		datastore.put(txn, user);
		txn.commit();
		return Response.ok().build();
	}

}