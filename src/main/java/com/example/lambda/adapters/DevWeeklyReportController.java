package com.example.lambda.adapters;

import com.example.lambda.application.GenerateWeeklyReportUseCase;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** Temporary development endpoint to exercise data acquisition. */
@IfBuildProfile("dev")
@ApplicationScoped
@Path("/dev/weekly-reports")
public class DevWeeklyReportController {
    private final GenerateWeeklyReportUseCase useCase;

    public DevWeeklyReportController(GenerateWeeklyReportUseCase useCase) {
        this.useCase = useCase;
    }

    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public String generate() {
        useCase.execute();
        return "Relatorio XLSX gerado e publicado no S3. Confira o bucket e o console.";
    }
}
