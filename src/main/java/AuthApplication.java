import auth.AuthTokenAuthenticator;
import auth.TokenAuthFilter;
import auth.UserRoleAuthorizer;
import com.mongodb.DB;
import com.mongodb.MongoClient;
import dao.UserDao;
import dao.UserDaoImpl;
import io.dropwizard.Application;
import io.dropwizard.auth.AuthDynamicFeature;
import io.dropwizard.auth.AuthValueFactoryProvider;
import io.dropwizard.setup.Environment;
import model.User;
import org.glassfish.jersey.filter.LoggingFilter;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;
import resource.AuthenticateResource;
import resource.SignupResource;
import service.AuthenticateService;
import service.AuthenticateServiceImpl;
import service.SignupService;
import service.SignupServiceImpl;

import java.net.UnknownHostException;

import static java.util.logging.Logger.getLogger;

public class AuthApplication extends Application<AuthConfiguration> {

    public static void main(String[] args) throws Exception {
        new AuthApplication().run(args);
    }

    @Override
    public String getName() {
        return "user-auth-service";
    }

    public void run(AuthConfiguration configuration,
                    Environment environment) throws Exception {

        DB db = setupMongoDB(configuration, environment);
        UserDao dao = new UserDaoImpl(db);
        SignupService signupService = new SignupServiceImpl(dao);
        AuthenticateService authenticateService = new AuthenticateServiceImpl(dao, configuration.getAuthTokenSecret());
        SignupResource signupResource = new SignupResource(signupService);
        AuthenticateResource authenticateResource = new AuthenticateResource(authenticateService);

        // register logging filter for all requests (not printing entity for security)
        environment.jersey().register(new LoggingFilter(getLogger(LoggingFilter.class.getName()), false));

        // register resources
        environment.jersey().register(signupResource);
        environment.jersey().register(authenticateResource);

        // register auth framework
//        registerAuth(configuration, environment, dao);
    }

    private void registerAuth(AuthConfiguration configuration, Environment environment, UserDao dao) {

        final TokenAuthFilter<User> tokenAuthFilter = new TokenAuthFilter.Builder<User>()
                .setAuthorizer(
                        new UserRoleAuthorizer()
                ).setAuthenticator(
                        new AuthTokenAuthenticator(configuration.getAuthTokenSecret(), dao)
                ).buildAuthFilter();

        environment.jersey().register(new AuthDynamicFeature(tokenAuthFilter));
        environment.jersey().register(RolesAllowedDynamicFeature.class);
        environment.jersey().register(new AuthValueFactoryProvider.Binder<>(User.class));
    }

    private DB setupMongoDB(AuthConfiguration configuration, Environment environment) throws UnknownHostException {
        MongoClient mongoClient = new MongoClient(configuration.getDatabase().getHost(), configuration.getDatabase().getPort());
        DB db = mongoClient.getDB(configuration.getDatabase().getName());
        MongoManaged mongoManaged = new MongoManaged(mongoClient);
        environment.lifecycle().manage(mongoManaged);
        return db;
    }
}
