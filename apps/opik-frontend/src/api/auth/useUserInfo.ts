import { useQuery } from "@tanstack/react-query";
import axios from "axios";
import { BASE_API_URL } from "@/api/api";

export type UserInfo = {
  loggedIn: boolean;
  userName: string | null;
};

export const useUserInfo = () => {
  return useQuery<UserInfo>({
    queryKey: ["userInfo"],
    queryFn: async () => {
      const { data } = await axios.get<UserInfo>(
        `${BASE_API_URL}/v1/session/auth/user`,
        {
          withCredentials: true,
        }
      );
      return data;
    },
    retry: false,
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
};
