package com.example.drowsiness_app

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

data class GeocodeResponse(val plus_code: PlusCode, val results: List<Result>, val status: String)

data class PlusCode(val compound_code: String, val global_code: String)

data class Result(
        val address_components: List<AddressComponent>,
        val formatted_address: String,
        val geometry: Geometry,
        val place_id: String,
        val types: List<String>,
        val postcode_localities: List<String>? // This field is not always present
)

data class AddressComponent(val long_name: String, val short_name: String, val types: List<String>)

data class Geometry(
        val bounds: Bounds,
        val location: Location,
        val location_type: String,
        val viewport: Viewport
)

data class Bounds(val northeast: Location, val southwest: Location)

data class Location(val lat: Double, val lng: Double)

data class Viewport(val northeast: Location, val southwest: Location)

interface ApiInterface {
    @GET("json")
    fun getGeocode(
            @Query("latlng") latlng: String,
            @Query("key") apiKey: String
    ): Call<GeocodeResponse>
}
