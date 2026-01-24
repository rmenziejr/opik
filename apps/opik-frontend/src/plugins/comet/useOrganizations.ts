import { QueryFunctionContext, useQuery } from "@tanstack/react-query";
import axios from "axios";
import api, { QueryConfig } from "./api";
import { Organization } from "./types";

const getOrganizations = async ({
  signal,
}: QueryFunctionContext): Promise<Organization[]> => {
  try {
    const { data } = await api.get<Organization[]>("/organizations", {
      params: { withoutExtendedData: true },
      signal,
    });
    return data;
  } catch (error) {
    // If comet endpoint fails, return empty array for basic auth mode
    if (axios.isAxiosError(error)) {
      return [];
    }
    throw error;
  }
};

export default function useOrganizations(
  options?: QueryConfig<Organization[]>,
) {
  return useQuery({
    queryKey: ["organizations", {}],
    queryFn: (context) => getOrganizations(context),
    ...options,
  });
}
