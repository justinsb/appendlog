package com.cloudata.appendlog;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.robotninjas.barge.NoLeaderException;
import org.robotninjas.barge.NotLeaderException;
import org.robotninjas.barge.RaftException;
import org.robotninjas.barge.RaftService;
import org.robotninjas.barge.Replica;
import org.robotninjas.barge.log.GetEntriesResult;
import org.robotninjas.barge.proto.RaftEntry.Entry;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;
import com.google.common.reflect.Reflection;
import com.sun.grizzly.http.SelectorThread;
import com.sun.jersey.api.container.grizzly.GrizzlyWebContainerFactory;

@Path("/")
public class BargeD {

    public static String PACKAGE_OPT = "com.sun.jersey.config.property.packages";

    private static Database database;

    // @GET
    // @Path("{key}")
    // @Produces(MediaType.APPLICATION_OCTET_STREAM)
    // public Response get(@PathParam("key") String key) {
    // byte[] data = database.get(key.getBytes());
    // return Response.ok(data).build();
    // }

    @GET
    // @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response get() {
        long index = database.commitIndex();
        return Response.ok(String.valueOf(index)).build();
    }

    @GET
    @Path("{key}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response get(@PathParam("key") String key) {
        long begin = Long.valueOf(key);
        int max = 1;
        GetEntriesResult entries = database.getEntriesFrom(begin, max);
        if (!entries.entries().isEmpty()) {
            Entry entry = entries.entries().get(0);
            byte[] data = entry.getCommand().toByteArray();
            return Response.ok(data).build();
        }

        return Response.status(Status.NOT_FOUND).build();
    }

    @POST
    // @Path("{key}")
    // @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public Response post(/* @PathParam("key") String key, */InputStream value) throws IOException {
        try {
            // byte[] k = key.getBytes();
            byte[] v = ByteStreams.toByteArray(value);
            // for (int i = 0; i < 10000; i++) {
            // database.appendToLog(k, v);
            // }
            // System.out.println("Wrote 10k items");

            if (!database.appendToLog(v)) {
                return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
            }

            return Response.ok().build();
        } catch (InterruptedException e) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
        } catch (NoLeaderException e) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
        } catch (NotLeaderException e) {
            Replica leader = e.getLeader();
            InetSocketAddress address = (InetSocketAddress) leader.address();
            URI uri = URI.create("http://" + address.getHostName() + ":" + address.getPort() /* + "/" + key */);
            System.out.println(uri);
            return Response.seeOther(uri).build();
        } catch (RaftException e) {
            return Response.serverError().build();
        }
    }

    public static void main(String... args) throws IOException {

        final int port = Integer.parseInt(args[0]);

        Replica local = Replica.fromString("localhost:" + (10000 + port));
        List<Replica> members = Lists.newArrayList(Replica.fromString("localhost:10001"),
                Replica.fromString("localhost:10002"), Replica.fromString("localhost:10003"));
        members.remove(local);

        File logDir = new File(args[0]);
        logDir.mkdir();

        database = new Database();

        final RaftService raft = RaftService.newBuilder().local(local).members(members).logDir(logDir).timeout(300)
                .build(database);

        database.init(raft);

        raft.startAsync().awaitRunning();

        final String baseUri = "http://localhost:" + (9990 + port) + "/";
        final Map<String, String> initParams = Maps.newHashMap();

        initParams.put(PACKAGE_OPT, Reflection.getPackageName(BargeD.class));

        final SelectorThread selector = GrizzlyWebContainerFactory.create(baseUri, initParams);

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                selector.stopEndpoint();
                raft.stopAsync().awaitTerminated();
            }
        });

    }
}
