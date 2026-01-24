import React, { useState } from "react";
import { useNavigate } from "@tanstack/react-router";
import axios from "axios";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useToast } from "@/components/ui/use-toast";
import Logo from "@/plugins/comet/Logo";
import useAppStore from "@/store/AppStore";
import { DEFAULT_WORKSPACE_NAME } from "@/constants/user";
import { BASE_API_URL } from "@/api/api";

const LoginPage: React.FC = () => {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const { toast } = useToast();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);

    try {
      const response = await axios.post(
        `${BASE_API_URL}/v1/session/auth/login`,
        { username, password },
        { withCredentials: true }
      );

      const data = response.data;

      if (data.success) {
        toast({
          title: "Login successful",
          description: "Welcome back!",
        });

        // Redirect to the default workspace home page
        navigate({
          to: "/$workspaceName/home",
          params: { workspaceName: DEFAULT_WORKSPACE_NAME },
        });
      } else {
        toast({
          title: "Login failed",
          description: data.message || "Invalid username or password",
          variant: "destructive",
        });
      }
    } catch (error) {
      toast({
        title: "Login error",
        description: "An error occurred while logging in",
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-muted/40">
      <div className="mb-8">
        <Logo expanded />
      </div>

      <div className="w-full max-w-md rounded-lg border bg-background p-8 shadow-lg">
        <h1 className="comet-title-l mb-2 text-center">Welcome to Opik</h1>
        <p className="comet-body-s mb-6 text-center text-muted-foreground">
          Sign in to access your workspace
        </p>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="username">Username</Label>
            <Input
              id="username"
              type="text"
              placeholder="Enter your username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              required
              autoFocus
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="password">Password</Label>
            <Input
              id="password"
              type="password"
              placeholder="Enter your password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </div>

          <Button type="submit" className="w-full" disabled={loading}>
            {loading ? "Signing in..." : "Sign in"}
          </Button>
        </form>

        <div className="mt-6 text-center text-xs text-muted-foreground">
          <p>
            Self-hosted Opik with basic authentication.
            <br />
            Contact your administrator if you need access.
          </p>
        </div>
      </div>
    </div>
  );
};

export default LoginPage;
