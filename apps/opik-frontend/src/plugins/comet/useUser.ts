import { QueryFunctionContext, useQuery } from "@tanstack/react-query";
import axios from "axios";
import api, { QueryConfig } from "./api";
import { User } from "./types";
import { BASE_API_URL } from "@/api/api";
import { DEFAULT_WORKSPACE_NAME } from "@/constants/user";

// Response type from the local basic auth endpoint
interface BasicAuthUserInfo {
  loggedIn: boolean;
  userName: string | null;
}

const getUser = async ({ signal }: QueryFunctionContext): Promise<User> => {
  try {
    // Try the comet/cloud auth endpoint first
    const { data } = await api.get<User>("/auth/test", {
      signal,
    });
    return data;
  } catch (error) {
    // If the comet endpoint fails (404, network error, etc.), try local basic auth
    if (axios.isAxiosError(error)) {
      const { data } = await axios.get<BasicAuthUserInfo>(
        `${BASE_API_URL}/v1/session/auth/user`,
        { signal, withCredentials: true }
      );

      // Map basic auth response to User type for compatibility
      return {
        apiKeys: [],
        defaultWorkspace: DEFAULT_WORKSPACE_NAME,
        email: "",
        gitHub: false,
        loggedIn: data.loggedIn,
        profileImages: { small: "", large: "" },
        suspended: false,
        userName: data.userName || "",
      };
    }
    throw error;
  }
};

export default function useUser(options?: QueryConfig<User>) {
  return useQuery({
    queryKey: ["user", {}],
    queryFn: (context) => getUser(context),
    ...options,
  });
}
