package nl.tomhanekamp.blespeedtest.server.api

import com.amazonaws.mobileconnectors.lambdainvoker.LambdaFunction
import nl.tomhanekamp.blespeedtest.server.model.Request
import nl.tomhanekamp.blespeedtest.server.model.Response

interface MyInterface {


    @LambdaFunction(functionName = "AndroidAppLambda")
    fun AndroidBackendLambdaFunction(request: Request?): Response?
}