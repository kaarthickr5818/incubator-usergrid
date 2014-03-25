/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.usergrid.persistence.graph;


import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import org.jukito.JukitoRunner;
import org.jukito.UseModules;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.lang3.time.StopWatch;

import org.apache.usergrid.persistence.collection.OrganizationScope;
import org.apache.usergrid.persistence.collection.cassandra.CassandraRule;
import org.apache.usergrid.persistence.collection.guice.MigrationManagerRule;
import org.apache.usergrid.persistence.graph.guice.TestGraphModule;
import org.apache.usergrid.persistence.graph.impl.SimpleSearchByEdgeType;
import org.apache.usergrid.persistence.model.entity.Id;
import org.apache.usergrid.persistence.model.util.UUIDGenerator;

import com.google.inject.Inject;

import rx.Observable;
import rx.Subscriber;

import static org.apache.usergrid.persistence.graph.test.util.EdgeTestUtils.createEdge;
import static org.apache.usergrid.persistence.graph.test.util.EdgeTestUtils.createId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


@RunWith( JukitoRunner.class )
@UseModules( TestGraphModule.class )
public class EdgeManagerStressTest {
    private static final Logger log = LoggerFactory.getLogger( EdgeManagerStressTest.class );

    @Inject
    private EdgeManagerFactory factory;

    @ClassRule
    public static CassandraRule rule = new CassandraRule();

    @Inject
    @Rule
    public MigrationManagerRule migrationManagerRule;


    protected OrganizationScope scope;


    @Before
    public void setup() {
        scope = mock( OrganizationScope.class );

        Id orgId = mock( Id.class );

        when( orgId.getType() ).thenReturn( "organization" );
        when( orgId.getUuid() ).thenReturn( UUIDGenerator.newTimeUUID() );

        when( scope.getOrganization() ).thenReturn( orgId );
    }


    @Test
    public void writeThousands() throws InterruptedException {
        EdgeGenerator generator = new EdgeGenerator() {

            private Set<Id> sourceIds = new HashSet<Id>();


            @Override
            public Edge newEdge() {
                Edge edge = createEdge( "source", "test", "target" );

                sourceIds.add( edge.getSourceNode() );

                return edge;
            }


            @Override
            public Observable<Edge> doSearch( final EdgeManager manager ) {


                final UUID uuid = UUIDGenerator.newTimeUUID();


                return Observable.create( new Observable.OnSubscribe<Edge>() {

                    @Override
                    public void call( final Subscriber<? super Edge> subscriber ) {
                        try {
                            for ( Id sourceId : sourceIds ) {

                                final Iterable<Edge> edges = manager.loadEdgesFromSource(
                                        new SimpleSearchByEdgeType( sourceId, "test", uuid, null ) )
                                                                    .toBlockingObservable().toIterable();

                                for ( Edge edge : edges ) {
                                    log.debug( "Firing on next for edge {}", edge );

                                    subscriber.onNext( edge );
                                }
                            }
                        }
                        catch ( Throwable throwable ) {
                            subscriber.onError( throwable );
                        }
                    }
                } );


                //TODO T.N keep this code it's exhibiting a failure /exception swallowing with RX when our scheduler
                // is full
                //
                //              return  Observable.create( new Observable.OnSubscribe<Edge>() {
                //
                //                    @Override
                //                    public void call( final Subscriber<? super Edge> subscriber ) {
                //                        for ( Id sourceId : sourceIds ) {
                //
                //                                            final Observable<Edge> edges =
                //                                                    manager.loadEdgesFromSource( new
                // SimpleSearchByEdgeType( sourceId, "test", uuid, null ) );
                //
                //                            edges.subscribe( new Action1<Edge>() {
                //                                @Override
                //                                public void call( final Edge edge ) {
                //                                    subscriber.onNext( edge );
                //                                }
                //                            },
                //
                //                            new Action1<Throwable>() {
                //                                @Override
                //                                public void call( final Throwable throwable ) {
                //                                    subscriber.onError( throwable );
                //                                }
                //                            });
                //                         }
                //                    }
                //                } ) ;


            }
        };

        doTest( generator );
    }

    @Test
    public void writeThousandsSingleSource() throws InterruptedException {
        EdgeGenerator generator = new EdgeGenerator() {

            private Id sourceId = createId( "source" );


            @Override
            public Edge newEdge() {
                Edge edge = createEdge( sourceId, "test", createId( "target" ) );


                return edge;
            }


            @Override
            public Observable<Edge> doSearch( final EdgeManager manager ) {
                UUID uuid = UUIDGenerator.newTimeUUID();

                return manager.loadEdgesFromSource( new SimpleSearchByEdgeType( sourceId, "test", uuid, null ) );
            }
        };

        doTest( generator );
    }

    @Test
       public void writeThousandsSingleTarget() throws InterruptedException {
           EdgeGenerator generator = new EdgeGenerator() {

               private Id targetId = createId( "target" );


               @Override
               public Edge newEdge() {
                   Edge edge = createEdge( createId( "source" ), "test", targetId );


                   return edge;
               }


               @Override
               public Observable<Edge> doSearch( final EdgeManager manager ) {
                   UUID uuid = UUIDGenerator.newTimeUUID();

                   return manager.loadEdgesToTarget( new SimpleSearchByEdgeType( targetId, "test", uuid, null ) );
               }
           };

           doTest( generator );
       }


    /**
     * Execute the test with the generator
     * @param generator
     * @throws InterruptedException
     */
    private void doTest( EdgeGenerator generator ) throws InterruptedException {
        EdgeManager manager = factory.createEdgeManager( scope );

        int limit = 10000;

        final StopWatch timer = new StopWatch();
        timer.start();
        final Set<Edge> ids = new HashSet<Edge>( limit );

        for ( int i = 0; i < limit; i++ ) {

            Edge edge = generator.newEdge();

            Edge returned = manager.writeEdge( edge ).toBlockingObservable().last();


            assertNotNull( "Returned has a version", returned.getVersion() );

            ids.add( returned );

            if ( i % 1000 == 0 ) {
                log.info( "   Wrote: " + i );
            }
        }

        timer.stop();
        log.info( "Total time to write {} entries {}ms", limit, timer.getTime() );
        timer.reset();

        timer.start();

        final CountDownLatch latch = new CountDownLatch( 1 );


        generator.doSearch( manager ).subscribe( new Subscriber<Edge>() {
            @Override
            public void onCompleted() {
                timer.stop();
                latch.countDown();
            }


            @Override
            public void onError( final Throwable throwable ) {
                fail( "Exception occurced " + throwable );
            }


            @Override
            public void onNext( final Edge edge ) {
                ids.remove( edge );
            }
        } );


        latch.await();


        assertEquals( 0, ids.size() );


        log.info( "Total time to read {} entries {}ms", limit, timer.getTime() );
    }


    private interface EdgeGenerator {

        /**
         * Create a new edge to persiste
         */
        public Edge newEdge();

        public Observable<Edge> doSearch( final EdgeManager manager );
    }
}