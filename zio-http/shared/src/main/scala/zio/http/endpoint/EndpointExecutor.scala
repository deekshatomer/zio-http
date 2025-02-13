/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http.endpoint

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.http._
import zio.http.codec._
import zio.http.endpoint.internal.EndpointClient

/**
 * A [[zio.http.endpoint.EndpointExecutor]] is responsible for taking an
 * endpoint invocation, and executing the invocation, returning the final
 * result, or failing with a pre-defined RPC error.
 */
final case class BatchedEndpointExecutor[R, Auth](
  client: Client,
  locator: EndpointLocator,
  authProvider: ZIO[R, Nothing, Auth],
  batchSize: Int = 10  // Define the batch size
) {
  private var requests: List[ZIO[R, Throwable, _]] = List()  // List to hold requests
  
  // Function to add requests to the batch
  def addToBatch[P, I, E, B, AuthT <: AuthType](
    invocation: Invocation[P, I, E, B, AuthT]
  ): Unit = {
    requests = requests :+ apply(invocation) // Add the request to the batch
    if (requests.size >= batchSize) {
      executeBatch()  // If batch is full, execute
    }
  }

  // Function to execute the batch of requests
  def executeBatch(): Unit = {
    val batch = ZIO.collectAll(requests)  // Collect all the requests in the batch
    // Execute batch (this is a simple example, you may need to modify depending on your logic)
    batch.foldM(
      error => ZIO.fail(error), // handle failure
      result => ZIO.succeed(result) // handle success
    )
  }

  // Original apply method for a single request
  def apply[P, I, E, B, AuthT <: AuthType](
    invocation: Invocation[P, I, E, B, AuthT]
  )(implicit
    combiner: Combiner[I, invocation.endpoint.authType.ClientRequirement],
    ev: Auth <:< invocation.endpoint.authType.ClientRequirement,
    trace: Trace
  ): ZIO[R with Scope, E, B] = {
    // Execution logic for a single request
    getClient(invocation.endpoint).orDie.flatMap { endpointClient =>
      endpointClient.execute(
        client,
        invocation,
        authProvider.asInstanceOf[URIO[R, endpointClient.endpoint.authType.ClientRequirement]]
      )(
        combiner.asInstanceOf[Combiner[I, endpointClient.endpoint.authType.ClientRequirement]],
        trace
      )
    }
  }
}

