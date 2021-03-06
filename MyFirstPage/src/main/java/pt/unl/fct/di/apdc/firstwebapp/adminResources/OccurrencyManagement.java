package pt.unl.fct.di.apdc.firstwebapp.adminResources;

import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.datastore.DatastoreService;

import pt.unl.fct.di.apdc.firstwebapp.resources.IntegrityLogsResource;
import pt.unl.fct.di.apdc.firstwebapp.util.Enums.LogOperation;
import pt.unl.fct.di.apdc.firstwebapp.util.Enums.LogType;
import pt.unl.fct.di.apdc.firstwebapp.util.Enums.OccurrencyFlags;
import pt.unl.fct.di.apdc.firstwebapp.util.objects.OccurrencyUpdateData;
import pt.unl.fct.di.apdc.firstwebapp.util.objects.SessionInfo;

public class OccurrencyManagement {
	
	
	public static Response confirmOccurrency(DatastoreService datastore, String ocID, Logger LOG, SessionInfo session) {
		Transaction txn = datastore.beginTransaction();
		Key ocKey = KeyFactory.createKey("Occurrency", ocID);
		try {
			LOG.info("Attempt to get ocurrency: " + ocID);
			Entity occurrency = datastore.get(txn, ocKey);
			LOG.info("Got occurrency");
			if(((String)occurrency.getProperty("flag")).equals("unconfirmed"))
				occurrency.setProperty("flag", OccurrencyFlags.confirmed.toString());
			datastore.put(txn, occurrency);
			txn.commit();
			LOG.info("Commited");
			IntegrityLogsResource.insertNewLog(LogOperation.Confirmation, new String[]{ocID}, LogType.Occurrency, session.username);
			LOG.info("Inserted new Log");
			return Response.ok().build();
		}catch(EntityNotFoundException e) {
			LOG.warning("Failed to locate ocurrency: " + ocID);
			return Response.status(Status.NOT_FOUND).build();
		}finally {
			if (txn.isActive()) {
				txn.rollback();
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}
		}
	}
	
	
	public static Response deleteOccurrency(DatastoreService datastore, String ocID, Logger LOG, SessionInfo session) {
		Transaction txn = datastore.beginTransaction();
		LOG.info("Deleting occurrency with id: " + ocID);
		Key ocKey = KeyFactory.createKey("Occurrency", ocID);
		datastore.delete(ocKey);
		txn.commit();
		LOG.info("Occurrency Deleted");
		IntegrityLogsResource.insertNewLog(LogOperation.Delete, new String[]{ocID}, LogType.Occurrency, session.username);
		return Response.ok().build();
	}
	
	
	public static Response updateOccurrency(OccurrencyUpdateData info, DatastoreService datastore, String ocID, Logger LOG){
		Transaction txn = datastore.beginTransaction();
		Key ocKey = KeyFactory.createKey("Occurrency", ocID);
		try {
			LOG.info("Attempt to get ocurrency: " + ocID);
			Entity occurrency = datastore.get(txn, ocKey);
			LOG.info("Got occurrency");
			
			datastore.put(txn, occurrency);
			txn.commit();
			IntegrityLogsResource.insertNewLog(LogOperation.Update, new String[]{ocID}, LogType.Occurrency, info.username);
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
}