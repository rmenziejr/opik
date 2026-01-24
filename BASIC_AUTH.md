# Basic Authentication Setup for Opik

This guide explains how to enable basic authentication for your self-hosted Opik installation.

## Overview

Opik supports basic authentication using a username and password configured via environment variables. This is designed for self-hosted installations where you want simple authentication without connecting to external services.

## Configuration

### Using Docker Compose

1. Create a `.env` file in the same directory as your `docker-compose.yaml`:

```bash
# Enable authentication
AUTH_ENABLED=true

# Set authentication mode to basic
AUTH_MODE=basic

# Set admin credentials
BASIC_AUTH_USERNAME=admin
BASIC_AUTH_PASSWORD=your-secure-password-here
```

2. Start Opik with the configuration:

```bash
docker-compose up -d
```

### Using Environment Variables Directly

You can also set environment variables directly in your docker-compose.yaml or when running the backend service:

```yaml
environment:
  AUTH_ENABLED: "true"
  AUTH_MODE: "basic"
  BASIC_AUTH_USERNAME: "admin"
  BASIC_AUTH_PASSWORD: "your-secure-password-here"
```

## Using the Application

### Logging In

1. Navigate to your Opik installation (e.g., `http://localhost:5173`)
2. You will be automatically redirected to the login page
3. Enter your configured username and password
4. Click "Sign in"

### Logging Out

To log out, you can:
1. Close your browser (session will expire)
2. Clear your browser cookies
3. Call the logout endpoint: `POST /api/v1/session/auth/logout`

## Security Considerations

⚠️ **Important Security Notes:**

1. **Use HTTPS in Production**: Always use HTTPS in production environments to protect credentials in transit.

2. **Strong Passwords**: Use strong, unique passwords. Consider using a password manager to generate secure passwords.

3. **Environment Variables**: Never commit passwords to version control. Always use environment variables or secrets management systems.

4. **Session Management**: Sessions are stored in-memory and will be lost if the backend service restarts. Users will need to log in again.

5. **Single User**: This basic auth implementation is designed for a single admin user. For multi-user scenarios, consider using the cloud version of Opik or implementing a more robust authentication system.

## API Endpoints

The following authentication endpoints are available:

### Login
```bash
POST /api/v1/session/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "your-password"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Login successful",
  "username": "admin"
}
```

### Logout
```bash
POST /api/v1/session/auth/logout
```

**Response:**
```json
{
  "success": true,
  "message": "Logout successful"
}
```

### Get Current User
```bash
GET /api/v1/session/auth/user
```

**Response:**
```json
{
  "loggedIn": true,
  "userName": "admin"
}
```

## Troubleshooting

### I forgot my password

To reset your password:
1. Stop the Opik services
2. Update the `BASIC_AUTH_PASSWORD` environment variable
3. Restart the Opik services
4. Log in with the new password

### I'm stuck in a redirect loop

This can happen if:
- The backend service is not running
- Authentication is misconfigured
- Cookies are blocked

Try:
1. Clear your browser cookies
2. Verify the backend service is running: `curl http://localhost:8080/health-check`
3. Check environment variables are set correctly
4. Check browser console for errors

### API calls are failing with 401 Unauthorized

Make sure you:
1. Have logged in successfully
2. Are sending cookies with API requests (`credentials: 'include'` in fetch)
3. Are using the correct API endpoints

## Disabling Authentication

To disable authentication:

1. Set `AUTH_ENABLED=false` in your environment variables or `.env` file
2. Restart the Opik services

```bash
AUTH_ENABLED=false
```

When authentication is disabled, the application will work as before with a default user.
