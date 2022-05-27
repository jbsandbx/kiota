package com.microsoft.kiota.http;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.util.regex.Pattern;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.microsoft.kiota.ApiClientBuilder;
import com.microsoft.kiota.ApiException;
import com.microsoft.kiota.RequestInformation;
import com.microsoft.kiota.RequestOption;
import com.microsoft.kiota.ResponseHandler;
import com.microsoft.kiota.authentication.AuthenticationProvider;
import com.microsoft.kiota.serialization.ParseNodeFactoryRegistry;
import com.microsoft.kiota.serialization.Parsable;
import com.microsoft.kiota.serialization.ParsableFactory;
import com.microsoft.kiota.serialization.ParseNode;
import com.microsoft.kiota.serialization.ParseNodeFactory;
import com.microsoft.kiota.serialization.SerializationWriterFactory;
import com.microsoft.kiota.serialization.SerializationWriterFactoryRegistry;
import com.microsoft.kiota.store.BackingStoreFactory;
import com.microsoft.kiota.store.BackingStoreFactorySingleton;

import kotlin.OptIn;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okhttp3.Response;
import okio.BufferedSink;

public class OkHttpRequestAdapter implements com.microsoft.kiota.RequestAdapter {
    private final static String contentTypeHeaderKey = "Content-Type";
    private final OkHttpClient client;
    private final AuthenticationProvider authProvider;
    private ParseNodeFactory pNodeFactory;
    private SerializationWriterFactory sWriterFactory;
    private String baseUrl = "";
    public void setBaseUrl(@Nonnull final String baseUrl) {
        this.baseUrl = Objects.requireNonNull(baseUrl);
    }
    @Nonnull
    public String getBaseUrl() {
        return baseUrl;
    }
    public OkHttpRequestAdapter(@Nonnull final AuthenticationProvider authenticationProvider){
        this(authenticationProvider, null, null, null);
    }
    public OkHttpRequestAdapter(@Nonnull final AuthenticationProvider authenticationProvider, @Nonnull final ParseNodeFactory parseNodeFactory) {
        this(authenticationProvider, parseNodeFactory, null, null);
        Objects.requireNonNull(parseNodeFactory, "parameter parseNodeFactory cannot be null");
    }
    public OkHttpRequestAdapter(@Nonnull final AuthenticationProvider authenticationProvider, @Nonnull final ParseNodeFactory parseNodeFactory, @Nullable final SerializationWriterFactory serializationWriterFactory) {
        this(authenticationProvider, parseNodeFactory, serializationWriterFactory, null);
        Objects.requireNonNull(serializationWriterFactory, "parameter serializationWriterFactory cannot be null");
    }
    public OkHttpRequestAdapter(@Nonnull final AuthenticationProvider authenticationProvider, @Nullable final ParseNodeFactory parseNodeFactory, @Nullable final SerializationWriterFactory serializationWriterFactory, @Nullable final OkHttpClient client) {
        this.authProvider = Objects.requireNonNull(authenticationProvider, "parameter authenticationProvider cannot be null");
        if(client == null) {
            this.client = KiotaClientFactory.Create().build();
        } else {
            this.client = client;
        }
        if(parseNodeFactory == null) {
            pNodeFactory = ParseNodeFactoryRegistry.defaultInstance;
        } else {
            pNodeFactory = parseNodeFactory;
        }

        if(serializationWriterFactory == null) {
            sWriterFactory = SerializationWriterFactoryRegistry.defaultInstance;
        } else {
            sWriterFactory = serializationWriterFactory;
        }
    }
    public SerializationWriterFactory getSerializationWriterFactory() {
        return sWriterFactory;
    }
    public void enableBackingStore(@Nullable final BackingStoreFactory backingStoreFactory) {
        this.pNodeFactory = Objects.requireNonNull(ApiClientBuilder.enableBackingStoreForParseNodeFactory(pNodeFactory));
        this.sWriterFactory = Objects.requireNonNull(ApiClientBuilder.enableBackingStoreForSerializationWriterFactory(sWriterFactory));
        if(backingStoreFactory != null) {
            BackingStoreFactorySingleton.instance = backingStoreFactory;
        }
    }
    @Nonnull
    public <ModelType extends Parsable> CompletableFuture<Iterable<ModelType>> sendCollectionAsync(@Nonnull final RequestInformation requestInfo, @Nonnull final ParsableFactory<ModelType> factory, @Nullable final ResponseHandler responseHandler, @Nullable final HashMap<String, ParsableFactory<? extends Parsable>> errorMappings) {
        Objects.requireNonNull(requestInfo, "parameter requestInfo cannot be null");
        Objects.requireNonNull(factory, "parameter factory cannot be null");

        return this.getHttpResponseMessage(requestInfo, null)
        .thenCompose(response -> {
            if(responseHandler == null) {
                try {
                    this.throwFailedResponse(response, errorMappings);
                    if(this.shouldReturnNull(response)) {
                        return CompletableFuture.completedStage(null);
                    }
                    final ParseNode rootNode = getRootParseNode(response);
                    final Iterable<ModelType> result = rootNode.getCollectionOfObjectValues(factory);
                    return CompletableFuture.completedStage(result);
                } catch(ApiException ex) {
                    return CompletableFuture.failedFuture(ex);
                } catch(IOException ex) {
                    return CompletableFuture.failedFuture(new RuntimeException("failed to read the response body", ex));
                } finally {
                    response.close();
                }
            } else {
                return responseHandler.handleResponseAsync(response, errorMappings);
            }
        });
    }
    @Nonnull
    public <ModelType extends Parsable> CompletableFuture<ModelType> sendAsync(@Nonnull final RequestInformation requestInfo, @Nonnull final ParsableFactory<ModelType> factory, @Nullable final ResponseHandler responseHandler, @Nullable final HashMap<String, ParsableFactory<? extends Parsable>> errorMappings) {
        Objects.requireNonNull(requestInfo, "parameter requestInfo cannot be null");
        Objects.requireNonNull(factory, "parameter factory cannot be null");

        return this.getHttpResponseMessage(requestInfo, null)
        .thenCompose(response -> {
            if(responseHandler == null) {
                try {
                    this.throwFailedResponse(response, errorMappings);
                    if(this.shouldReturnNull(response)) {
                        return CompletableFuture.completedStage(null);
                    }
                    final ParseNode rootNode = getRootParseNode(response);
                    final ModelType result = rootNode.getObjectValue(factory);
                    return CompletableFuture.completedStage(result);
                } catch(ApiException ex) {
                    return CompletableFuture.failedFuture(ex);
                } catch(IOException ex) {
                    return CompletableFuture.failedFuture(new RuntimeException("failed to read the response body", ex));
                } finally {
                    response.close();
                }
            } else {
                return responseHandler.handleResponseAsync(response, errorMappings);
            }
        });
    }
    private String getMediaTypeAndSubType(final MediaType mediaType) {
        return mediaType.type() + "/" + mediaType.subtype();
    }
    @Nonnull
    public <ModelType> CompletableFuture<ModelType> sendPrimitiveAsync(@Nonnull final RequestInformation requestInfo, @Nonnull final Class<ModelType> targetClass, @Nullable final ResponseHandler responseHandler, @Nullable final HashMap<String, ParsableFactory<? extends Parsable>> errorMappings) {
        return this.getHttpResponseMessage(requestInfo, null)
        .thenCompose(response -> {
            if(responseHandler == null) {
                try {
                    this.throwFailedResponse(response, errorMappings);
                    if(this.shouldReturnNull(response)) {
                        return CompletableFuture.completedStage(null);
                    }
                    if(targetClass == Void.class) {
                        return CompletableFuture.completedStage(null);
                    } else {
                        if(targetClass == InputStream.class) {
                            final ResponseBody body = response.body();
                            final InputStream rawInputStream = body.byteStream();
                            return CompletableFuture.completedStage((ModelType)rawInputStream);
                        }
                        final ParseNode rootNode = getRootParseNode(response);
                        Object result;
                        if(targetClass == Boolean.class) {
                            result = rootNode.getBooleanValue();
                        } else if(targetClass == Byte.class) {
                            result = rootNode.getByteValue();
                        } else if(targetClass == String.class) {
                            result = rootNode.getStringValue();
                        } else if(targetClass == Short.class) {
                            result = rootNode.getShortValue();
                        } else if(targetClass == BigDecimal.class) {
                            result = rootNode.getBigDecimalValue();
                        } else if(targetClass == Double.class) {
                            result = rootNode.getDoubleValue();
                        } else if(targetClass == Integer.class) {
                            result = rootNode.getIntegerValue();
                        } else if(targetClass == Float.class) {
                            result = rootNode.getFloatValue();
                        } else if(targetClass == Long.class) {
                            result = rootNode.getLongValue();
                        } else if(targetClass == UUID.class) {
                            result = rootNode.getUUIDValue();
                        } else if(targetClass == OffsetDateTime.class) {
                            result = rootNode.getOffsetDateTimeValue();
                        } else if(targetClass == LocalDate.class) {
                            result = rootNode.getLocalDateValue();
                        } else if(targetClass == LocalTime.class) {
                            result = rootNode.getLocalTimeValue();
                        } else if(targetClass == Period.class) {
                            result = rootNode.getPeriodValue();
                        } else if(targetClass == byte[].class) {
                            result = rootNode.getByteArrayValue();
                        } else {
                            throw new RuntimeException("unexpected payload type " + targetClass.getName());
                        }
                        return CompletableFuture.completedStage((ModelType)result);
                    }
                } catch(ApiException ex) {
                    return CompletableFuture.failedFuture(ex);
                } catch(IOException ex) {
                    return CompletableFuture.failedFuture(new RuntimeException("failed to read the response body", ex));
                } finally {
                    response.close();
                }
            } else {
                return responseHandler.handleResponseAsync(response, errorMappings);
            }
        });
    }
    public <ModelType> CompletableFuture<Iterable<ModelType>> sendPrimitiveCollectionAsync(@Nonnull final RequestInformation requestInfo, @Nonnull final Class<ModelType> targetClass, @Nullable final ResponseHandler responseHandler, @Nullable final HashMap<String, ParsableFactory<? extends Parsable>> errorMappings) {
        Objects.requireNonNull(requestInfo, "parameter requestInfo cannot be null");

        return this.getHttpResponseMessage(requestInfo, null)
        .thenCompose(response -> {
            if(responseHandler == null) {
                try {
                    this.throwFailedResponse(response, errorMappings);
                    if(this.shouldReturnNull(response)) {
                        return CompletableFuture.completedStage(null);
                    }
                    final ParseNode rootNode = getRootParseNode(response);
                    final Iterable<ModelType> result = rootNode.getCollectionOfPrimitiveValues(targetClass);
                    return CompletableFuture.completedStage(result);
                } catch(ApiException ex) {
                    return CompletableFuture.failedFuture(ex);
                } catch(IOException ex) {
                    return CompletableFuture.failedFuture(new RuntimeException("failed to read the response body", ex));
                } finally {
                    response.close();
                }
            } else {
                return responseHandler.handleResponseAsync(response, errorMappings);
            }
        });
    }
    private ParseNode getRootParseNode(final Response response) throws IOException {
        try (final ResponseBody body = response.body()) {
            try (final InputStream rawInputStream = body.byteStream()) {
                final ParseNode rootNode = pNodeFactory.getParseNode(getMediaTypeAndSubType(body.contentType()), rawInputStream);
                return rootNode;
            }
        }
    }
    private boolean shouldReturnNull(final Response response) {
        final int statusCode = response.code();
        return statusCode == 204;
    }
    private Response throwFailedResponse(final Response response, final HashMap<String, ParsableFactory<? extends Parsable>> errorMappings) throws IOException, ApiException {
        if (response.isSuccessful()) return response;

        final String statusCodeAsString = Integer.toString(response.code());
        final Integer statusCode = response.code();
        if (errorMappings == null ||
           !errorMappings.containsKey(statusCodeAsString) &&
           !(statusCode >= 400 && statusCode < 500 && errorMappings.containsKey("4XX")) &&
           !(statusCode >= 500 && statusCode < 600 && errorMappings.containsKey("5XX"))) {
            throw new ApiException("the server returned an unexpected status code and no error class is registered for this code " + statusCode);
        }
        final ParsableFactory<? extends Parsable> errorClass = errorMappings.containsKey(statusCodeAsString) ?
                                                    errorMappings.get(statusCodeAsString) :
                                                    (statusCode >= 400 && statusCode < 500 ?
                                                        errorMappings.get("4XX") :
                                                        errorMappings.get("5XX"));
        try {
            final ParseNode rootNode = getRootParseNode(response);
            final Parsable error = rootNode.getObjectValue(errorClass);
            if (error instanceof ApiException) {
                throw (ApiException)error;
            } else {
                throw new ApiException("unexpected error type " + error.getClass().getName());
            }
        } finally {
            response.close();
        }
    }
    private final static String claimsKey = "claims";
    private CompletableFuture<Response> getHttpResponseMessage(@Nonnull final RequestInformation requestInfo, @Nullable final String claims) {
        Objects.requireNonNull(requestInfo, "parameter requestInfo cannot be null");
        this.setBaseUrlForRequestInformation(requestInfo);
        final Map<String, Object> additionalContext = new HashMap<>();
        if(claims != null && !claims.isEmpty()) {
            additionalContext.put(claimsKey, claims);
        }
        return this.authProvider.authenticateRequest(requestInfo, additionalContext).thenCompose(x -> {
            try {
                final OkHttpCallbackFutureWrapper wrapper = new OkHttpCallbackFutureWrapper();
                this.client.newCall(getRequestFromRequestInformation(requestInfo)).enqueue(wrapper);
                return wrapper.future;
            } catch (URISyntaxException | MalformedURLException ex) {
                var result = new CompletableFuture<Response>();
                result.completeExceptionally(ex);
                return result;
            }
        }).thenCompose(x -> this.retryCAEResponseIfRequired(x, requestInfo, claims));
    }
    private final static Pattern bearerPattern = Pattern.compile("^Bearer\\s.*", Pattern.CASE_INSENSITIVE);
    private final static Pattern claimsPattern = Pattern.compile("\\s?claims=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private CompletableFuture<Response> retryCAEResponseIfRequired(@Nonnull final Response response, @Nonnull final RequestInformation requestInfo, @Nullable final String claims) {
        final var responseClaims = this.getClaimsFromResponse(response, requestInfo, claims);
        if (responseClaims != null && !responseClaims.isEmpty()) {
            if(requestInfo.content != null && requestInfo.content.markSupported()) {
                try {
                    requestInfo.content.reset();
                } catch (IOException ex) {
                    return CompletableFuture.failedFuture(ex);
                }
            }
            response.close();
            return this.getHttpResponseMessage(requestInfo, responseClaims);
        }

        return CompletableFuture.completedFuture(response);
    }
    String getClaimsFromResponse(@Nonnull final Response response, @Nonnull final RequestInformation requestInfo, @Nullable final String claims) {
        if(response.code() == 401 &&
           (claims == null || claims.isEmpty()) && // we avoid infinite loops and retry only once
           (requestInfo.content == null || requestInfo.content.markSupported())) {
               final var authenticateHeader = response.headers("WWW-Authenticate");
               if(authenticateHeader != null && !authenticateHeader.isEmpty()) {
                    String rawHeaderValue = null;
                    for(final var authenticateEntry: authenticateHeader) {
                        final var matcher = bearerPattern.matcher(authenticateEntry);
                        if(matcher.matches()) {
                            rawHeaderValue = authenticateEntry.replaceFirst("^Bearer\\s", "");
                            break;
                        }
                    }
                    if (rawHeaderValue != null) {
                        final var parameters = rawHeaderValue.split(",");
                        for(final var parameter: parameters) {
                            final var matcher = claimsPattern.matcher(parameter);
                            if(matcher.matches()) {
                                return matcher.group(1);
                            }
                        }
                    }
                }
            }
            return null;
    }
    private void setBaseUrlForRequestInformation(@Nonnull final RequestInformation requestInfo) {
        Objects.requireNonNull(requestInfo);
        requestInfo.pathParameters.put("baseurl", getBaseUrl());
    }
    private Request getRequestFromRequestInformation(@Nonnull final RequestInformation requestInfo) throws URISyntaxException, MalformedURLException {
        final RequestBody body = requestInfo.content == null ? null :
                                new RequestBody() {
                                    @Override
                                    public MediaType contentType() {
                                        final Map<String, String> requestHeaders = requestInfo.getRequestHeaders();
                                        final String contentType = requestHeaders.containsKey(contentTypeHeaderKey) ? requestHeaders.get(contentTypeHeaderKey) : "";
                                        if(contentType.isEmpty()) {
                                            return null;
                                        } else {
                                            return MediaType.parse(contentType);
                                        }
                                    }

                                    @Override
                                    public void writeTo(BufferedSink sink) throws IOException {
                                        sink.write(requestInfo.content.readAllBytes());
                                        //TODO this is dirty and is probably going to use a lot of memory for large payloads, loop on a buffer instead
                                    }

                                };
        final Request.Builder requestBuilder = new Request.Builder()
                                            .url(requestInfo.getUri().toURL())
                                            .method(requestInfo.httpMethod.toString(), body);
        for (final Map.Entry<String,String> header : requestInfo.getRequestHeaders().entrySet()) {
            requestBuilder.addHeader(header.getKey(), header.getValue());
        }

        for(final RequestOption option : requestInfo.getRequestOptions()) {
            requestBuilder.tag(option.getType(), option);
        }
        return requestBuilder.build();
    }
}
